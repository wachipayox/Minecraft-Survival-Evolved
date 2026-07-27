package com.wachi.mse.entity.dinosaur.control;

import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Locomotor steering for dinosaurs.
 *
 * <p>Vanilla's move control is retained for path following, jumping and
 * strafing, but its yaw interpolation is limited by distance travelled. The
 * optional look-turn maneuver also walks forward while steering. Consequently
 * neither navigation nor a look target can rotate the body in place.</p>
 */
public final class DinosaurMoveControl extends MoveControl {
    private static final int LOOK_TURN_REQUEST_TICKS = 3;

    private final DinosaurOrientationConfig config;
    private float lookTurnTargetYaw;
    private int lookTurnTicks;
    private double lastControlX;
    private double lastControlZ;
    private double lastHorizontalDistance;
    private double lastRiddenControlX;
    private double lastRiddenControlZ;
    private int lastRiddenControlTick = Integer.MIN_VALUE;

    public DinosaurMoveControl(Mob mob, DinosaurOrientationConfig config) {
        super(mob);
        this.config = config;
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
     * Applies rider steering through the same displacement-limited yaw rule
     * used by path following. Looking around while stationary therefore
     * cannot rotate the dinosaur in place.
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
        if (this.operation != Operation.WAIT) {
            super.tick();
            ageLookTurnRequest();
            return;
        }

        if (canPerformLookTurn()) {
            tickLookTurn();
        } else {
            super.tick();
        }
        ageLookTurnRequest();
    }

    @Override
    protected float rotlerp(float currentYaw, float targetYaw, float ignoredMaximumChange) {
        float maximumChange = maximumYawChangeForLastDisplacement();
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
        return remainingYaw > this.config.bodyTurnStopYawDegrees();
    }

    private void tickLookTurn() {
        float movementSpeed = (float) (
                this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        * this.config.lookTurnSpeedModifier());
        this.mob.setSpeed(movementSpeed);
        this.mob.setXxa(0.0F);
        this.mob.setZza(1.0F);
        this.mob.setYRot(this.rotlerp(
                this.mob.getYRot(),
                this.lookTurnTargetYaw,
                this.config.maxBodyYawChangeDegreesPerTick()));
    }

    private float maximumYawChangeForLastDisplacement() {
        return maximumYawChangeForDisplacement(this.lastHorizontalDistance);
    }

    private float maximumYawChangeForDisplacement(
            double horizontalDistance) {
        if ((!this.mob.onGround() && !this.mob.isInWater())
                || horizontalDistance < this.config.minimumTurningDistance()) {
            return 0.0F;
        }
        return Math.min(
                this.config.maxBodyYawChangeDegreesPerTick(),
                (float) (horizontalDistance
                        * this.config.steeringDegreesPerBlock()));
    }

    private void captureDistanceSinceLastControlTick() {
        double currentX = this.mob.getX();
        double currentZ = this.mob.getZ();
        this.lastHorizontalDistance = Math.sqrt(
                Mth.square(currentX - this.lastControlX)
                        + Mth.square(currentZ - this.lastControlZ));
        this.lastControlX = currentX;
        this.lastControlZ = currentZ;
    }

    private void ageLookTurnRequest() {
        if (this.lookTurnTicks > 0) {
            this.lookTurnTicks--;
        }
    }
}
