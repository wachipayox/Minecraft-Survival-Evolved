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
 * over. The desired push speed ramps up, is restored after ground friction,
 * and remains latched briefly after becoming airborne, so the fall does not
 * alternate between acceleration and pauses at the collision-box boundary.
 * Dynamic gait balance is deliberately left to locomotion rather than
 * applying a static-polygon rule while a foot is in swing.</p>
 */
public final class DinosaurBalanceController {
    private static final int SAMPLE_INTERVAL_TICKS = 2;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    private State state = State.STABLE;
    private int unstableTicks;
    private DinosaurStabilityAssessment lastAssessment;
    private Vec3 committedFallDirection = Vec3.ZERO;
    private double committedFallPushSpeed;
    private boolean becameAirborne;
    private int airborneAssistTicksRemaining;

    public void tick(
            Mob dinosaur,
            DinosaurProceduralConfig config) {
        if (dinosaur.level().isClientSide()
                || dinosaur.isNoAi()
                || !dinosaur.isAlive()
                || dinosaur.isPassenger()
                || dinosaur.isInWater()) {
            this.reset();
            return;
        }

        if (this.state == State.FALLING) {
            this.tickCommittedFall(dinosaur, config);
            return;
        }
        if (!dinosaur.onGround()) {
            this.reset();
            return;
        }

        if ((dinosaur.tickCount + dinosaur.getId()) % SAMPLE_INTERVAL_TICKS == 0
                || this.lastAssessment == null) {
            DinosaurProceduralPose pose =
                    DinosaurTerrainSampler.sampleAuthoritative(dinosaur, config);
            this.lastAssessment = pose.stability();
            this.updateState(dinosaur, pose, config.stability());
        }

        if (this.state == State.FALLING) {
            this.tickCommittedFall(dinosaur, config);
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
        this.committedFallDirection = Vec3.ZERO;
        this.committedFallPushSpeed = 0.0;
        this.becameAirborne = false;
        this.airborneAssistTicksRemaining = 0;
    }

    private void updateState(
            Mob dinosaur,
            DinosaurProceduralPose pose,
            DinosaurStabilityConfig config) {
        DinosaurStabilityAssessment assessment = pose.stability();
        boolean activelyWalking =
                pose.gait().activity() > config.maximumActivityForStaticBalance()
                        || dinosaur.getNavigation().isInProgress()
                        || dinosaur.getMoveControl().hasWanted();
        if (!assessment.requiresRecovery()
                || assessment.fallDirectionWorld().lengthSqr()
                        <= DIRECTION_EPSILON) {
            this.state = State.STABLE;
            this.unstableTicks = 0;
            this.committedFallDirection = Vec3.ZERO;
            return;
        }
        if (activelyWalking && this.state != State.FALLING) {
            this.state = State.STABLE;
            this.unstableTicks = 0;
            this.committedFallDirection = Vec3.ZERO;
            return;
        }

        this.unstableTicks = Math.min(
                config.recoveryTicks(),
                this.unstableTicks + SAMPLE_INTERVAL_TICKS);
        if (this.unstableTicks >= config.recoveryTicks()) {
            if (this.state != State.FALLING) {
                this.beginFall(
                        assessment.fallDirectionWorld().normalize(),
                        config);
                dinosaur.getNavigation().stop();
                dinosaur.getMoveControl().setWait();
            }
        } else {
            this.state = State.RECOVERING;
        }
    }

    private void beginFall(
            Vec3 direction,
            DinosaurStabilityConfig config) {
        this.state = State.FALLING;
        this.committedFallDirection = direction;
        this.committedFallPushSpeed = 0.0;
        this.becameAirborne = false;
        this.airborneAssistTicksRemaining = config.airborneFallAssistTicks();
    }

    private void tickCommittedFall(
            Mob dinosaur,
            DinosaurProceduralConfig config) {
        DinosaurStabilityConfig stability = config.stability();
        dinosaur.getNavigation().stop();
        dinosaur.getMoveControl().setWait();
        this.committedFallPushSpeed = Math.min(
                stability.maximumFallHorizontalSpeed(),
                this.committedFallPushSpeed
                        + stability.fallAccelerationPerTick());
        applyFallVelocity(
                dinosaur,
                stability,
                this.committedFallDirection,
                this.committedFallPushSpeed);

        if (!dinosaur.onGround()) {
            this.becameAirborne = true;
            this.airborneAssistTicksRemaining--;
            if (this.airborneAssistTicksRemaining <= 0) {
                this.reset();
            }
            return;
        }

        if (this.becameAirborne) {
            DinosaurProceduralPose pose =
                    DinosaurTerrainSampler.sampleAuthoritative(dinosaur, config);
            this.lastAssessment = pose.stability();
            if (!this.lastAssessment.requiresRecovery()) {
                this.reset();
            } else {
                this.becameAirborne = false;
                this.airborneAssistTicksRemaining =
                        stability.airborneFallAssistTicks();
            }
        }
    }

    private static void applyFallVelocity(
            Mob dinosaur,
            DinosaurStabilityConfig config,
            Vec3 direction,
            double desiredSpeed) {
        Vec3 movement = dinosaur.getDeltaMovement();
        double speedAlongFall =
                movement.x * direction.x + movement.z * direction.z;
        double correction = desiredSpeed - speedAlongFall;
        if (correction <= 0.0) {
            return;
        }
        if (speedAlongFall < 0.0) {
            correction = Math.min(
                    correction,
                    config.fallAccelerationPerTick() * 2.0);
        }
        // Entity#push also marks the velocity as externally changed, keeping
        // client interpolation in step with the authoritative correction.
        dinosaur.push(
                direction.x * correction,
                0.0,
                direction.z * correction);
    }

    public enum State {
        STABLE,
        RECOVERING,
        FALLING
    }
}
