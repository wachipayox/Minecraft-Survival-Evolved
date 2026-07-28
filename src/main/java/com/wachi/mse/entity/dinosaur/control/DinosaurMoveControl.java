package com.wachi.mse.entity.dinosaur.control;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurNavigationConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Locomotor steering for dinosaurs.
 *
 * <p>Vanilla's move control is retained for path following, jumping and
 * strafing, but its yaw interpolation is limited by distance travelled. The
 * optional look-turn maneuver also walks forward while steering. A bounded
 * in-place recovery turn is enabled only after a movement request has made no
 * progress for the species-configured delay.</p>
 */
public final class DinosaurMoveControl extends MoveControl {
    private static final int LOOK_TURN_REQUEST_TICKS = 3;
    private static final double IDLE_SPEED_FRACTION = 0.30;
    private static final double PLAYER_BASE_WALK_SPEED = 0.10;
    private static final double PLAYER_STANDING_HEIGHT = 1.80;

    private final DinosaurOrientationConfig fallbackOrientationConfig;
    private final DinosaurNavigationConfig fallbackNavigationConfig;
    private float lookTurnTargetYaw;
    private int lookTurnTicks;
    private double lastControlX;
    private double lastControlZ;
    private double lastHorizontalDistance;
    private double lastRiddenControlX;
    private double lastRiddenControlZ;
    private int lastRiddenControlTick = Integer.MIN_VALUE;
    private int stalledTicks;
    private int riddenStalledTicks;
    private boolean idleMovement;

    public DinosaurMoveControl(
            Mob mob,
            DinosaurOrientationConfig config,
            DinosaurNavigationConfig navigationConfig) {
        super(mob);
        this.fallbackOrientationConfig = config;
        this.fallbackNavigationConfig = navigationConfig;
        this.lastControlX = mob.getX();
        this.lastControlZ = mob.getZ();
        this.lastRiddenControlX = mob.getX();
        this.lastRiddenControlZ = mob.getZ();
    }

    public void requestLookTurn(float targetYawDegrees) {
        this.lookTurnTargetYaw = targetYawDegrees;
        this.lookTurnTicks = LOOK_TURN_REQUEST_TICKS;
    }

    public void cancelLookTurn() {
        this.lookTurnTicks = 0;
    }

    public boolean isLookTurnActive() {
        return this.lookTurnTicks > 0;
    }

    /**
     * Calculates a calm absolute roaming speed from the live entity size.
     *
     * <p>Idle travel normally uses 30% of maximum speed. Its lower bound
     * scales from a player's base walking speed using entity height, and the
     * result is always capped at the dinosaur's maximum speed.</p>
     */
    public static float idleSpeedFor(Mob mob) {
        double maximumSpeed =
                mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double sizeAdjustedMinimum =
                PLAYER_BASE_WALK_SPEED
                        * mob.getBbHeight()
                        / PLAYER_STANDING_HEIGHT;
        return (float) Math.min(
                maximumSpeed,
                Math.max(
                        maximumSpeed * IDLE_SPEED_FRACTION,
                        sizeAdjustedMinimum));
    }

    public static double idleSpeedModifierFor(Mob mob) {
        double maximumSpeed =
                mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return maximumSpeed > 1.0E-6
                ? idleSpeedFor(mob) / maximumSpeed
                : 0.0;
    }

    public void beginIdleMovement() {
        this.idleMovement = true;
    }

    public void endIdleMovement() {
        this.idleMovement = false;
    }

    /**
     * Applies rider steering through the same displacement-limited yaw rule
     * used by path following. Looking around while stationary does not rotate
     * the dinosaur, except when forward input remains physically blocked long
     * enough to activate recovery.
     */
    public void steerRiddenToward(float targetYawDegrees) {
        double currentX = this.mob.getX();
        double currentZ = this.mob.getZ();
        double travelledX = currentX - this.lastRiddenControlX;
        double travelledZ = currentZ - this.lastRiddenControlZ;
        double lastTickDistance =
                this.lastRiddenControlTick == this.mob.tickCount - 1
                        ? Math.sqrt(
                                Mth.square(travelledX)
                                        + Mth.square(travelledZ))
                        : 0.0;
        this.lastRiddenControlX = currentX;
        this.lastRiddenControlZ = currentZ;
        this.lastRiddenControlTick = this.mob.tickCount;
        float maximumChange =
                maximumYawChangeForDisplacement(lastTickDistance);
        DinosaurNavigationConfig navigation = navigationConfig();
        if (lastTickDistance < navigation.minimumProgressBlocks()) {
            this.riddenStalledTicks++;
        } else {
            this.riddenStalledTicks = 0;
        }
        if (this.riddenStalledTicks
                >= navigation.stuckTurnDelayTicks()) {
            maximumChange = Math.max(
                    maximumChange,
                    recoveryYawChange());
        }
        if (maximumChange > 0.0F) {
            this.mob.setYRot(super.rotlerp(
                    this.mob.getYRot(),
                    targetYawDegrees,
                    maximumChange));
        }
    }

    @Override
    public void setWait() {
        super.setWait();
        this.cancelLookTurn();
    }

    @Override
    public void tick() {
        captureDistanceSinceLastControlTick();
        boolean normalizeForwardInput =
                hasAutonomousForwardRequest();
        if (this.operation != Operation.WAIT) {
            super.tick();
        } else if (canPerformLookTurn()) {
            tickLookTurn();
        } else {
            super.tick();
        }
        ageLookTurnRequest();
        if (this.idleMovement
                && this.mob.getControllingPassenger() == null) {
            /*
             * Navigation and look-turn transitions may both write speed. This
             * final absolute cap keeps bends as calm as straight idle travel.
             */
            this.mob.setSpeed(idleSpeedFor(this.mob));
        }
        if (normalizeForwardInput
                && this.mob.getControllingPassenger() == null) {
            /*
             * Mob#setSpeed also writes zza=speed. Leaving that untouched makes
             * autonomous travel multiply the requested speed by itself, while
             * ridden travel receives the player's normalized forward input.
             * Keep speed as the absolute locomotion value and normalize only
             * the input component so AI and rider use identical physics.
             */
            this.mob.setZza(1.0F);
        }
    }

    @Override
    protected float rotlerp(float currentYaw, float targetYaw, float ignoredMaximumChange) {
        float maximumChange = maximumYawChangeForLastDisplacement();
        if (this.stalledTicks >= navigationConfig().stuckTurnDelayTicks()
                && this.mob.onGround()
                && !this.mob.isInWater()) {
            maximumChange = Math.max(
                    maximumChange,
                    recoveryYawChange());
        }
        if (maximumChange <= 0.0F) {
            return currentYaw;
        }
        return super.rotlerp(currentYaw, targetYaw, maximumChange);
    }

    private boolean canPerformLookTurn() {
        if (this.lookTurnTicks <= 0
                || !this.mob.onGround()
                || this.mob.isInWater()
                || this.mob.isPassenger()) {
            return false;
        }

        float remainingYaw = Math.abs(Mth.wrapDegrees(
                this.lookTurnTargetYaw - this.mob.getYRot()));
        return remainingYaw
                > orientationConfig().bodyTurnStopYawDegrees();
    }

    private void tickLookTurn() {
        DinosaurOrientationConfig orientation = orientationConfig();
        float movementSpeed = this.mob.getTarget() == null
                ? idleSpeedFor(this.mob)
                : (float) (
                        this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)
                                * orientation.lookTurnSpeedModifier());
        this.mob.setSpeed(movementSpeed);
        this.mob.setXxa(0.0F);
        this.mob.setZza(1.0F);
        this.mob.setYRot(this.rotlerp(
                this.mob.getYRot(),
                this.lookTurnTargetYaw,
                orientation.maxBodyYawChangeDegreesPerTick()));
    }

    private float maximumYawChangeForLastDisplacement() {
        return maximumYawChangeForDisplacement(this.lastHorizontalDistance);
    }

    private float maximumYawChangeForDisplacement(
            double horizontalDistance) {
        DinosaurOrientationConfig orientation = orientationConfig();
        if ((!this.mob.onGround() && !this.mob.isInWater())
                || horizontalDistance
                        < orientation.minimumTurningDistance()) {
            return 0.0F;
        }
        return Math.min(
                orientation.maxBodyYawChangeDegreesPerTick(),
                (float) (horizontalDistance
                        * orientation.steeringDegreesPerBlock()));
    }

    private void captureDistanceSinceLastControlTick() {
        double currentX = this.mob.getX();
        double currentZ = this.mob.getZ();
        this.lastHorizontalDistance = Math.sqrt(
                Mth.square(currentX - this.lastControlX)
                        + Mth.square(currentZ - this.lastControlZ));
        this.lastControlX = currentX;
        this.lastControlZ = currentZ;
        boolean intendsToMove = this.operation != Operation.WAIT
                || this.lookTurnTicks > 0;
        if (intendsToMove
                && this.mob.onGround()
                && this.lastHorizontalDistance
                        < navigationConfig().minimumProgressBlocks()) {
            this.stalledTicks++;
        } else {
            this.stalledTicks = 0;
        }
    }

    private void ageLookTurnRequest() {
        if (this.lookTurnTicks > 0) {
            this.lookTurnTicks--;
        }
    }

    private boolean hasAutonomousForwardRequest() {
        if (this.operation == Operation.JUMPING) {
            return true;
        }
        if (this.operation != Operation.MOVE_TO) {
            return false;
        }
        double x = this.wantedX - this.mob.getX();
        double y = this.wantedY - this.mob.getY();
        double z = this.wantedZ - this.mob.getZ();
        return x * x + y * y + z * z >= 2.5000003E-7F;
    }

    private DinosaurOrientationConfig orientationConfig() {
        return this.mob instanceof ProceduralDinosaur dinosaur
                ? dinosaur.proceduralConfig().orientation()
                : this.fallbackOrientationConfig;
    }

    private DinosaurNavigationConfig navigationConfig() {
        return this.mob instanceof ProceduralDinosaur dinosaur
                ? dinosaur.proceduralConfig().navigation()
                : this.fallbackNavigationConfig;
    }

    /**
     * Stuck recovery may rotate without displacement, but never faster than
     * the live scale-aware angular cap. This preserves recovery for a blocked
     * animal without letting a giant pivot like its unscaled prototype.
     */
    private float recoveryYawChange() {
        return Math.min(
                navigationConfig().stuckTurnDegreesPerTick(),
                orientationConfig().maxBodyYawChangeDegreesPerTick());
    }
}
