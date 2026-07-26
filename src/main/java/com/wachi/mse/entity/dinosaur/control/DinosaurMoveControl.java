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

    public DinosaurMoveControl(Mob mob, DinosaurOrientationConfig config) {
        super(mob);
        this.config = config;
        this.lastControlX = mob.getX();
        this.lastControlZ = mob.getZ();
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

    @Override
    public void setWait() {
        super.setWait();
        this.cancelLookTurn();
    }

    @Override
    public void tick() {
        captureDistanceSinceLastControlTick();
        boolean pathMovement = this.operation == Operation.MOVE_TO;
        float pathHeadingError = pathMovement
                ? headingErrorTo(this.wantedX, this.wantedZ)
                : 0.0F;
        if (this.operation != Operation.WAIT) {
            super.tick();
            if (pathMovement) {
                this.mob.setSpeed(
                        this.mob.getSpeed()
                                * pathSpeedMultiplier(pathHeadingError));
            }
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

    private float headingErrorTo(double targetX, double targetZ) {
        float targetYaw = (float) (
                Mth.atan2(targetZ - this.mob.getZ(), targetX - this.mob.getX())
                        * 180.0F
                        / Math.PI)
                - 90.0F;
        return Math.abs(Mth.wrapDegrees(targetYaw - this.mob.getYRot()));
    }

    private float pathSpeedMultiplier(float headingError) {
        float startSlowing = this.config.bodyTurnStopYawDegrees();
        float fullySlowed = 90.0F;
        float progress = Mth.clamp(
                (headingError - startSlowing) / (fullySlowed - startSlowing),
                0.0F,
                1.0F);
        progress = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(
                progress,
                1.0F,
                (float) this.config.lookTurnSpeedModifier());
    }

    private float maximumYawChangeForLastDisplacement() {
        if ((!this.mob.onGround() && !this.mob.isInWater())
                || this.lastHorizontalDistance < this.config.minimumTurningDistance()) {
            return 0.0F;
        }
        return Math.min(
                this.config.maxBodyYawChangeDegreesPerTick(),
                (float) (this.lastHorizontalDistance
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
