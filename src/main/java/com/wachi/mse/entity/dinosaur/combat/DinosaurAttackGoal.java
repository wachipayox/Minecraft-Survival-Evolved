package com.wachi.mse.entity.dinosaur.combat;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import com.wachi.mse.entity.dinosaur.control.DinosaurMoveControl;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal direct pursuit without vanilla path finding.
 *
 * <p>The dinosaur always runs straight along its current forward axis. Outside
 * a narrow alignment tolerance it simultaneously corrects its body through
 * the shortest angular direction. No A* waypoint can therefore feed a circular
 * turn. Starting an attack removes input but retains physical momentum so
 * ground friction supplies a short coast.</p>
 */
public final class DinosaurAttackGoal extends Goal {
    private static final float MAX_ALIGNMENT_TOLERANCE_DEGREES =
            10.0F;
    private static final float PURSUIT_SLOWDOWN_START_DEGREES =
            45.0F;
    private static final float ORBIT_DETECTION_MINIMUM_YAW_DEGREES =
            20.0F;
    private static final int ORBIT_SAMPLE_TICKS = 10;
    private static final int ORBIT_STALLED_WINDOWS = 3;
    private static final int BREAKAWAY_MINIMUM_TICKS = 14;
    private static final int BREAKAWAY_RANDOM_TICKS = 11;
    private static final float BREAKAWAY_MINIMUM_DEGREES = 105.0F;
    private static final float BREAKAWAY_RANDOM_DEGREES = 35.0F;
    private static final double DIRECT_RUN_AHEAD_BODY_LENGTHS =
            2.0;

    private final DinosaurEntity dinosaur;
    private final DinosaurCombatController combat;
    private final double speedModifier;
    private boolean turningManeuver;
    private int orbitSampleTicks;
    private int stalledOrbitWindows;
    private int breakawayTicks;
    private int breakawayCooldownTicks;
    private float breakawayYawOffset;
    private double lastOrbitSampleDistance = Double.NaN;

    public DinosaurAttackGoal(
            DinosaurEntity dinosaur,
            DinosaurCombatController combat,
            double speedModifier) {
        this.dinosaur = dinosaur;
        this.combat = combat;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.dinosaur.getTarget();
        return validTarget(target);
    }

    @Override
    public boolean canContinueToUse() {
        return validTarget(this.dinosaur.getTarget());
    }

    @Override
    public void start() {
        this.turningManeuver = false;
        resetOrbitDetection();
        this.dinosaur.setAggressive(true);
        this.dinosaur.getNavigation().stop();
        if (this.dinosaur.getMoveControl()
                instanceof DinosaurMoveControl moveControl) {
            moveControl.endIdleMovement();
        }
    }

    @Override
    public void stop() {
        this.turningManeuver = false;
        resetOrbitDetection();
        LivingEntity target = this.dinosaur.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.dinosaur.setTarget(null);
        }
        this.dinosaur.setAggressive(false);
        coastToStop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.dinosaur.getTarget();
        if (!validTarget(target)) {
            return;
        }
        this.dinosaur.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.dinosaur.activeAttack() != null) {
            coastToStop();
            return;
        }

        float directTargetYaw = yawToward(target);
        float directYawError = Mth.wrapDegrees(
                directTargetYaw - this.dinosaur.getYRot());
        DinosaurOrientationConfig orientation =
                this.dinosaur.proceduralConfig().orientation();
        float alignmentTolerance = Math.min(
                MAX_ALIGNMENT_TOLERANCE_DEGREES,
                orientation.bodyTurnStopYawDegrees());
        updateOrbitDetection(
                this.dinosaur.distanceTo(target),
                Math.abs(directYawError));

        float steeringTargetYaw = directTargetYaw;
        if (this.breakawayTicks > 0) {
            steeringTargetYaw = Mth.wrapDegrees(
                    directTargetYaw + this.breakawayYawOffset);
            this.breakawayTicks--;
            this.turningManeuver = true;
        }
        float steeringYawError = Mth.wrapDegrees(
                steeringTargetYaw - this.dinosaur.getYRot());
        updateTurningManeuver(
                Math.abs(steeringYawError),
                alignmentTolerance);

        if (Math.abs(steeringYawError) > alignmentTolerance) {
            float nextYaw = Mth.approachDegrees(
                    this.dinosaur.getYRot(),
                    steeringTargetYaw,
                    orientation.maxBodyYawChangeDegreesPerTick());
            this.dinosaur.setYRot(nextYaw);
            this.dinosaur.yBodyRot = nextYaw;
        }

        if (this.combat.start(target)) {
            coastToStop();
            return;
        }

        double runAhead = Math.max(
                1.0,
                this.dinosaur.getBbWidth()
                        * DIRECT_RUN_AHEAD_BODY_LENGTHS);
        double yawRadians =
                Math.toRadians(this.dinosaur.getYRot());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        double pursuitSpeedModifier = pursuitSpeedModifier(
                orientation);
        this.dinosaur.getMoveControl().setWantedPosition(
                this.dinosaur.getX() + forwardX * runAhead,
                this.dinosaur.getY(),
                this.dinosaur.getZ() + forwardZ * runAhead,
                this.speedModifier * pursuitSpeedModifier);
    }

    /**
     * Keeps a badly aligned pursuit moving at a stable cornering speed.
     * Hysteresis is important here: recovering full speed as soon as the error
     * drops below 45 degrees can widen the curve again and produce an orbit.
     * Once committed, the maneuver therefore continues until the body is
     * genuinely aligned with the target.
     */
    private double pursuitSpeedModifier(
            DinosaurOrientationConfig orientation) {
        if (!this.turningManeuver) {
            return 1.0;
        }
        return Mth.clamp(
                orientation.lookTurnSpeedModifier(),
                0.05,
                1.0);
    }

    private void updateTurningManeuver(
            float absoluteYawError,
            float alignmentTolerance) {
        if (this.turningManeuver) {
            if (absoluteYawError <= alignmentTolerance) {
                this.turningManeuver = false;
            }
        } else if (absoluteYawError
                > PURSUIT_SLOWDOWN_START_DEGREES) {
            this.turningManeuver = true;
        }
    }

    /**
     * Detects a sustained orbit by sampling radial progress instead of reacting
     * to one noisy tick. The escape is intentionally not identical every time:
     * it commits to a slightly different outward heading and duration, creates
     * room, then hands control back to direct pursuit.
     */
    private void updateOrbitDetection(
            double distance,
            float absoluteDirectYawError) {
        if (this.breakawayCooldownTicks > 0) {
            this.breakawayCooldownTicks--;
        }
        if (this.breakawayTicks > 0) {
            this.lastOrbitSampleDistance = distance;
            this.orbitSampleTicks = 0;
            return;
        }
        if (++this.orbitSampleTicks < ORBIT_SAMPLE_TICKS) {
            return;
        }
        this.orbitSampleTicks = 0;
        double requiredProgress = Math.max(
                0.05,
                this.dinosaur.getBbWidth() * 0.025);
        boolean closing = Double.isFinite(this.lastOrbitSampleDistance)
                && this.lastOrbitSampleDistance - distance
                        >= requiredProgress;
        if (closing
                || absoluteDirectYawError
                        < ORBIT_DETECTION_MINIMUM_YAW_DEGREES) {
            this.stalledOrbitWindows = 0;
        } else if (Double.isFinite(this.lastOrbitSampleDistance)) {
            this.stalledOrbitWindows++;
        }
        this.lastOrbitSampleDistance = distance;

        if (this.stalledOrbitWindows < ORBIT_STALLED_WINDOWS
                || this.breakawayCooldownTicks > 0) {
            return;
        }
        startBreakaway();
    }

    private void startBreakaway() {
        float directYawError = Mth.wrapDegrees(
                yawToward(this.dinosaur.getTarget())
                        - this.dinosaur.getYRot());
        float side = directYawError < 0.0F ? -1.0F : 1.0F;
        if (this.dinosaur.getRandom().nextFloat() < 0.20F) {
            side = -side;
        }
        this.breakawayYawOffset = side
                * (BREAKAWAY_MINIMUM_DEGREES
                        + this.dinosaur.getRandom().nextFloat()
                                * BREAKAWAY_RANDOM_DEGREES);
        this.breakawayTicks = BREAKAWAY_MINIMUM_TICKS
                + this.dinosaur.getRandom()
                        .nextInt(BREAKAWAY_RANDOM_TICKS);
        this.breakawayCooldownTicks = 40
                + this.dinosaur.getRandom().nextInt(31);
        this.stalledOrbitWindows = 0;
        this.orbitSampleTicks = 0;
        this.lastOrbitSampleDistance = Double.NaN;
    }

    private void resetOrbitDetection() {
        this.orbitSampleTicks = 0;
        this.stalledOrbitWindows = 0;
        this.breakawayTicks = 0;
        this.breakawayCooldownTicks = 0;
        this.breakawayYawOffset = 0.0F;
        this.lastOrbitSampleDistance = Double.NaN;
    }

    private float yawToward(LivingEntity target) {
        Vec3 offset = target.position()
                .subtract(this.dinosaur.position());
        return (float) (
                Mth.atan2(offset.z, offset.x)
                        * 180.0F
                        / Math.PI)
                - 90.0F;
    }

    /**
     * Removes locomotor input but deliberately preserves delta movement.
     * Ground friction then produces a short physical coast into the attack
     * instead of an instantaneous stop.
     */
    private void coastToStop() {
        this.dinosaur.getNavigation().stop();
        this.dinosaur.getMoveControl().setWait();
        this.dinosaur.setXxa(0.0F);
        this.dinosaur.setZza(0.0F);
    }

    private static boolean validTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && (!(target instanceof Player player)
                        || (!player.isCreative() && !player.isSpectator()));
    }
}
