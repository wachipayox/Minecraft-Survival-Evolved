package com.wachi.mse.entity.dinosaur.control;

import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps the logical head direction inside the configured cervical range and
 * asks the locomotor controller for an arcing turn when the target lies beyond
 * the comfortable range.
 */
public final class DinosaurLookControl extends LookControl {
    private final DinosaurOrientationConfig config;

    public DinosaurLookControl(Mob mob, DinosaurOrientationConfig config) {
        super(mob);
        this.config = config;
    }

    /**
     * Lets a rider aim the neck without binding the body or the camera to the
     * same rotation.
     */
    public void tickRidden(Player controller) {
        this.lookAtCooldown = 0;
        if (this.mob.getMoveControl() instanceof DinosaurMoveControl movement) {
            movement.cancelLookTurn();
        }

        float bodyYaw = this.mob.getYRot();
        float relativeTargetYaw = Mth.wrapDegrees(
                controller.getYRot() - bodyYaw);
        float clampedRelativeYaw = Mth.clamp(
                relativeTargetYaw,
                -this.config.maxNeckYawDegrees(),
                this.config.maxNeckYawDegrees());
        this.mob.yHeadRot = this.rotateTowards(
                this.mob.yHeadRot,
                bodyYaw + clampedRelativeYaw,
                this.config.headYawSpeedDegreesPerTick());
        this.mob.yHeadRot = Mth.rotateIfNecessary(
                this.mob.yHeadRot,
                bodyYaw,
                this.config.maxNeckYawDegrees());

        float targetPitch = this.toVisualPitch(controller.getXRot());
        this.mob.setXRot(this.rotateTowards(
                this.mob.getXRot(),
                targetPitch,
                this.config.headPitchSpeedDegreesPerTick()));
    }

    @Override
    public void tick() {
        DinosaurMoveControl movement = this.mob.getMoveControl() instanceof DinosaurMoveControl control
                ? control
                : null;
        Optional<Float> desiredYaw = Optional.empty();
        Optional<Float> desiredPitch = Optional.empty();
        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
            desiredYaw = this.getYRotD();
            desiredPitch = this.getXRotD();
        }

        if (desiredYaw.isPresent()) {
            float bodyYaw = this.mob.getYRot();
            float targetYaw = desiredYaw.get();
            float relativeTargetYaw = Mth.wrapDegrees(targetYaw - bodyYaw);
            float clampedRelativeYaw = Mth.clamp(
                    relativeTargetYaw,
                    -this.config.maxNeckYawDegrees(),
                    this.config.maxNeckYawDegrees());
            float yawSpeed = Math.min(
                    this.yMaxRotSpeed,
                    this.config.headYawSpeedDegreesPerTick());
            this.mob.yHeadRot = this.rotateTowards(
                    this.mob.yHeadRot,
                    bodyYaw + clampedRelativeYaw,
                    yawSpeed);
            this.mob.yHeadRot = Mth.rotateIfNecessary(
                    this.mob.yHeadRot,
                    bodyYaw,
                    this.config.maxNeckYawDegrees());
            updateLookTurnRequest(movement, targetYaw, Math.abs(relativeTargetYaw));
        } else {
            this.mob.yHeadRot = this.rotateTowards(
                    this.mob.yHeadRot,
                    this.mob.getYRot(),
                    this.config.neckRecenteringSpeedDegreesPerTick());
            if (movement != null) {
                movement.cancelLookTurn();
            }
        }

        float targetPitch = desiredPitch
                .map(this::toVisualPitch)
                .orElse(0.0F);
        float pitchSpeed = desiredPitch.isPresent()
                ? Math.min(this.xMaxRotAngle, this.config.headPitchSpeedDegreesPerTick())
                : this.config.neckRecenteringSpeedDegreesPerTick();
        this.mob.setXRot(this.rotateTowards(
                this.mob.getXRot(),
                targetPitch,
                pitchSpeed));
    }

    /**
     * Minecraft pitch is negative when looking up, while the GeckoLib
     * dinosaur rig uses positive X rotation for an upward neck bend. Keep
     * that convention conversion in one place so ridden and AI look targets
     * cannot silently diverge again.
     */
    private float toVisualPitch(float minecraftPitch) {
        return Mth.clamp(
                -minecraftPitch,
                -this.config.maxPitchDownDegrees(),
                this.config.maxPitchUpDegrees());
    }

    private void updateLookTurnRequest(
            DinosaurMoveControl movement,
            float targetYaw,
            float yawError) {
        if (movement == null
                || !this.mob.getNavigation().isDone()
                || !this.mob.onGround()
                || this.mob.getTarget() == null) {
            if (movement != null) {
                movement.cancelLookTurn();
            }
            return;
        }

        boolean beyondStart = yawError > this.config.bodyTurnStartYawDegrees();
        boolean keepTurning = movement.isLookTurnActive()
                && yawError > this.config.bodyTurnStopYawDegrees();
        if (beyondStart || keepTurning) {
            movement.requestLookTurn(targetYaw);
        } else {
            movement.cancelLookTurn();
        }
    }
}
