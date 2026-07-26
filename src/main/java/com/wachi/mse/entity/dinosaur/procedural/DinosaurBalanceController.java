package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurStabilityConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative ledge recovery for a procedural dinosaur.
 *
 * <p>The terrain probe runs every other server tick, staggered by entity ID.
 * Once a stationary centre of mass remains outside the support polygon for
 * the configured grace period, a bounded horizontal acceleration moves the
 * main collision box off the ledge and lets normal Minecraft gravity take
 * over. The acceleration is additive and never replaces an opposing velocity
 * in one tick. Dynamic gait balance is deliberately left to locomotion rather
 * than applying a static-polygon rule while a foot is in swing; an already
 * committed fall remains latched so its own movement cannot cancel it.</p>
 */
public final class DinosaurBalanceController {
    private static final int SAMPLE_INTERVAL_TICKS = 2;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    private State state = State.STABLE;
    private int unstableTicks;
    private DinosaurStabilityAssessment lastAssessment;

    public void tick(
            Mob dinosaur,
            DinosaurProceduralConfig config) {
        if (dinosaur.level().isClientSide()
                || dinosaur.isNoAi()
                || !dinosaur.isAlive()
                || dinosaur.isPassenger()
                || dinosaur.isInWater()
                || !dinosaur.onGround()) {
            this.reset();
            return;
        }

        if ((dinosaur.tickCount + dinosaur.getId()) % SAMPLE_INTERVAL_TICKS == 0
                || this.lastAssessment == null) {
            DinosaurProceduralPose pose =
                    DinosaurTerrainSampler.sampleAuthoritative(dinosaur, config);
            this.lastAssessment = pose.stability();
            this.updateState(pose, config.stability());
        }

        if (this.state == State.FALLING && this.lastAssessment != null) {
            applyFallAcceleration(
                    dinosaur,
                    config.stability(),
                    this.lastAssessment.fallDirectionWorld());
        }
    }

    public State state() {
        return this.state;
    }

    public int unstableTicks() {
        return this.unstableTicks;
    }

    public DinosaurStabilityAssessment lastAssessment() {
        return this.lastAssessment;
    }

    public void reset() {
        this.state = State.STABLE;
        this.unstableTicks = 0;
        this.lastAssessment = null;
    }

    private void updateState(
            DinosaurProceduralPose pose,
            DinosaurStabilityConfig config) {
        DinosaurStabilityAssessment assessment = pose.stability();
        boolean dynamicBalance =
                pose.gait().activity() > config.maximumActivityForStaticBalance();
        if (!assessment.requiresRecovery()
                || assessment.fallDirectionWorld().lengthSqr()
                        <= DIRECTION_EPSILON) {
            this.state = State.STABLE;
            this.unstableTicks = 0;
            return;
        }
        if (dynamicBalance && this.state != State.FALLING) {
            this.state = State.STABLE;
            this.unstableTicks = 0;
            return;
        }

        this.unstableTicks = Math.min(
                config.recoveryTicks(),
                this.unstableTicks + SAMPLE_INTERVAL_TICKS);
        if (this.unstableTicks >= config.recoveryTicks()) {
            if (this.state != State.FALLING) {
                this.state = State.FALLING;
            }
        } else {
            this.state = State.RECOVERING;
        }
    }

    private static void applyFallAcceleration(
            Mob dinosaur,
            DinosaurStabilityConfig config,
            Vec3 direction) {
        Vec3 movement = dinosaur.getDeltaMovement();
        double speedAlongFall =
                movement.x * direction.x + movement.z * direction.z;
        if (speedAlongFall >= config.maximumFallHorizontalSpeed()) {
            return;
        }
        double acceleration = Math.min(
                config.fallAccelerationPerTick(),
                config.maximumFallHorizontalSpeed() - speedAlongFall);
        dinosaur.setDeltaMovement(
                movement.x + direction.x * acceleration,
                movement.y,
                movement.z + direction.z * acceleration);
    }

    public enum State {
        STABLE,
        RECOVERING,
        FALLING
    }
}
