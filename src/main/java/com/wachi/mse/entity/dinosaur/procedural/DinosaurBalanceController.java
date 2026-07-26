package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurStabilityConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative ledge recovery for a procedural dinosaur.
 *
 * <p>The terrain probe runs every other server tick, staggered by entity ID.
 * Once the centre of mass remains outside the support polygon for the
 * configured grace period, a bounded horizontal contribution moves the main
 * collision box off the ledge and lets normal Minecraft gravity take over.
 * Ordinary swing phases remain the responsibility of locomotion, but actual
 * missing or unreachable terrain is evaluated even while navigating. The
 * contribution is reconstructed after friction and added to navigation
 * velocity rather than replacing the route's movement.</p>
 */
public final class DinosaurBalanceController {
    private static final int SAMPLE_INTERVAL_TICKS = 2;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    private State state = State.STABLE;
    private int unstableTicks;
    private DinosaurStabilityAssessment lastAssessment;
    private Vec3 committedFallDirection = Vec3.ZERO;
    private double targetFallPushSpeed;
    private double appliedFallPushSpeed;
    private double fallVelocityRetention;
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
        this.targetFallPushSpeed = 0.0;
        this.appliedFallPushSpeed = 0.0;
        this.fallVelocityRetention = 0.0;
        this.becameAirborne = false;
        this.airborneAssistTicksRemaining = 0;
    }

    private void updateState(
            Mob dinosaur,
            DinosaurProceduralPose pose,
            DinosaurStabilityConfig config) {
        DinosaurStabilityAssessment assessment = pose.stability();
        if (!requiresRecovery(dinosaur, pose, config)) {
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
        this.targetFallPushSpeed = 0.0;
        this.appliedFallPushSpeed = 0.0;
        this.fallVelocityRetention = 0.0;
        this.becameAirborne = false;
        this.airborneAssistTicksRemaining = config.airborneFallAssistTicks();
    }

    private void tickCommittedFall(
            Mob dinosaur,
            DinosaurProceduralConfig config) {
        DinosaurStabilityConfig stability = config.stability();
        if (dinosaur.onGround()
                && (dinosaur.tickCount + dinosaur.getId())
                        % SAMPLE_INTERVAL_TICKS == 0) {
            DinosaurProceduralPose pose =
                    DinosaurTerrainSampler.sampleAuthoritative(dinosaur, config);
            this.lastAssessment = pose.stability();
            if (!requiresRecovery(dinosaur, pose, stability)) {
                this.reset();
                return;
            }
        }

        double retainedFallSpeed =
                this.appliedFallPushSpeed * this.fallVelocityRetention;
        this.targetFallPushSpeed = Math.min(
                stability.maximumFallHorizontalSpeed(),
                this.targetFallPushSpeed
                        + stability.fallAccelerationPerTick());
        applyFallVelocityContribution(
                dinosaur,
                this.committedFallDirection,
                this.targetFallPushSpeed - retainedFallSpeed);
        this.appliedFallPushSpeed = this.targetFallPushSpeed;
        this.fallVelocityRetention =
                horizontalVelocityRetention(dinosaur);

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
            if (!requiresRecovery(dinosaur, pose, stability)) {
                this.reset();
            } else {
                this.becameAirborne = false;
                this.airborneAssistTicksRemaining =
                        stability.airborneFallAssistTicks();
            }
        }
    }

    private static boolean requiresRecovery(
            Mob dinosaur,
            DinosaurProceduralPose pose,
            DinosaurStabilityConfig config) {
        DinosaurStabilityAssessment assessment = pose.stability();
        if (!assessment.requiresRecovery()
                || assessment.fallDirectionWorld().lengthSqr()
                        <= DIRECTION_EPSILON) {
            return false;
        }
        boolean activelyWalking =
                pose.gait().activity() > config.maximumActivityForStaticBalance()
                        || dinosaur.getNavigation().isInProgress()
                        || dinosaur.getMoveControl().hasWanted();
        return !activelyWalking || hasTerrainLoss(pose);
    }

    private static boolean hasTerrainLoss(DinosaurProceduralPose pose) {
        for (DinosaurLegPose leg : pose.legs()) {
            if (!leg.terrainContact() || !leg.reachable()) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalVelocityRetention(Mob dinosaur) {
        if (!dinosaur.onGround()) {
            return 0.91;
        }
        var supportPosition =
                dinosaur.getBlockPosBelowThatAffectsMyMovement();
        float friction = dinosaur.level()
                .getBlockState(supportPosition)
                .getFriction(
                        dinosaur.level(),
                        supportPosition,
                        dinosaur);
        return friction * 0.91;
    }

    private static void applyFallVelocityContribution(
            Mob dinosaur,
            Vec3 direction,
            double correction) {
        if (correction <= 0.0) {
            return;
        }
        // Entity#push also marks the velocity as externally changed, keeping
        // client interpolation in step with the authoritative correction. It
        // adds this contribution to navigation instead of replacing it.
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
