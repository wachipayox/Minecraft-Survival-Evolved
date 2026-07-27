package com.wachi.mse.entity.dinosaur.navigation;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurNavigationConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground navigation with curvature-aware waypoint following.
 *
 * <p>Normal-sized species retain Minecraft's collision-aware A* grid. Its
 * exact nodes are converted into a short pure-pursuit target whose look-ahead
 * follows the species' turning radius. Giant species use a bounded local
 * collision fan instead of asking the grid search to expand an impractical
 * volume.</p>
 */
public final class DinosaurGroundPathNavigation extends GroundPathNavigation {
    private static final int HARD_MAX_LOOK_AHEAD_NODES = 64;
    private static final double MIN_LOOK_AHEAD_BLOCKS = 0.75;
    private static final double MIN_COLLISION_SAMPLE_STEP = 0.2;
    private static final double MAX_COLLISION_SAMPLE_STEP = 0.75;
    private static final double COLLISION_EPSILON = 1.0E-4;
    private static final int LOCAL_PLAN_INTERVAL_TICKS = 3;

    private final DinosaurNavigationConfig navigationConfig;
    private Entity movingTarget;
    private Vec3 fixedTarget;
    private double localSpeedModifier;
    private int nextLocalPlanTick;
    private float cachedLocalYaw;
    private double cachedLocalLift;

    public DinosaurGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
        this.navigationConfig = ((ProceduralDinosaur) mob)
                .proceduralConfig()
                .navigation();
        this.setMaxVisitedNodesMultiplier(
                this.navigationConfig.maxVisitedNodesMultiplier());
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        DinosaurOrientationConfig orientation =
                ((ProceduralDinosaur) this.mob).proceduralConfig().orientation();
        return new DinosaurPathFinder(
                this.nodeEvaluator,
                maxVisitedNodes,
                this.mob,
                orientation);
    }

    @Override
    public void tick() {
        if (usesLocalPlanner()) {
            tickLocalPlanner();
            return;
        }
        super.tick();
        Path currentPath = this.path;
        if (currentPath == null
                || currentPath.isDone()
                || !this.canUpdatePath()) {
            return;
        }

        Vec3 exactTarget = currentPath.getNextEntityPos(this.mob);
        Vec3 steeringTarget = calculateSteeringTarget(currentPath);
        double steeringGroundY = this.getGroundY(steeringTarget);
        Vec3 groundedSteeringTarget = new Vec3(
                steeringTarget.x,
                steeringGroundY,
                steeringTarget.z);
        if (horizontalDistanceSquared(groundedSteeringTarget, exactTarget) > 1.0E-6
                && isSafeDirectTarget(groundedSteeringTarget)) {
            this.mob.getMoveControl().setWantedPosition(
                    groundedSteeringTarget.x,
                    groundedSteeringTarget.y,
                    groundedSteeringTarget.z,
                    this.speedModifier);
        }
    }

    @Override
    public boolean moveTo(Entity target, double speedModifier) {
        if (usesLocalPlanner()) {
            this.movingTarget = target;
            this.fixedTarget = null;
            this.localSpeedModifier = speedModifier;
            return true;
        }
        return super.moveTo(target, speedModifier);
    }

    @Override
    public boolean moveTo(
            double x,
            double y,
            double z,
            double speedModifier) {
        if (usesLocalPlanner()) {
            this.movingTarget = null;
            this.fixedTarget = new Vec3(x, y, z);
            this.localSpeedModifier = speedModifier;
            return true;
        }
        return super.moveTo(x, y, z, speedModifier);
    }

    @Override
    public void stop() {
        super.stop();
        this.movingTarget = null;
        this.fixedTarget = null;
    }

    @Override
    public boolean isDone() {
        return usesLocalPlanner()
                ? this.movingTarget == null && this.fixedTarget == null
                : super.isDone();
    }

    private boolean usesLocalPlanner() {
        return ((ProceduralDinosaur) this.mob)
                        .proceduralConfig()
                        .modelScale()
                >= this.navigationConfig.localPlannerScaleThreshold();
    }

    private void tickLocalPlanner() {
        Vec3 target = this.movingTarget != null
                ? this.movingTarget.position()
                : this.fixedTarget;
        if (target == null) {
            return;
        }
        Vec3 offset = target.subtract(this.mob.position());
        double horizontalDistance = offset.horizontalDistance();
        if (this.movingTarget == null
                && horizontalDistance
                        <= Math.max(0.5, this.mob.getBbWidth() * 0.25)) {
            this.fixedTarget = null;
            return;
        }

        if (this.mob.tickCount >= this.nextLocalPlanTick) {
            chooseLocalSteering(offset);
            this.nextLocalPlanTick =
                    this.mob.tickCount + LOCAL_PLAN_INTERVAL_TICKS;
        }
        double steeringDistance = Math.max(
                1.0,
                Math.min(
                        horizontalDistance,
                        Math.max(
                                this.navigationConfig.minimumLocalProbeDistance(),
                                this.mob.getBbWidth() * 0.5)));
        double yawRadians = Math.toRadians(this.cachedLocalYaw);
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        this.mob.getMoveControl().setWantedPosition(
                this.mob.getX() + forwardX * steeringDistance,
                this.mob.getY() + this.cachedLocalLift,
                this.mob.getZ() + forwardZ * steeringDistance,
                this.localSpeedModifier);
    }

    private void chooseLocalSteering(Vec3 targetOffset) {
        float desiredYaw = (float) (
                Mth.atan2(targetOffset.z, targetOffset.x)
                        * 180.0F
                        / Math.PI)
                - 90.0F;
        float bestYaw = desiredYaw;
        double bestLift = 0.0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int sample = 0;
                sample <= this.navigationConfig.steeringSamplesPerSide();
                sample++) {
            int variants = sample == 0 ? 1 : 2;
            for (int variant = 0; variant < variants; variant++) {
                float signedSample = sample == 0
                        ? 0.0F
                        : sample * (variant == 0 ? 1.0F : -1.0F);
                float candidateYaw = desiredYaw
                        + signedSample
                                * this.navigationConfig.steeringSampleDegrees();
                LocalProbe probe = probeDirection(candidateYaw);
                if (!probe.passable()) {
                    continue;
                }
                double score = -Math.abs(signedSample) - probe.lift() * 0.2;
                if (score > bestScore) {
                    bestScore = score;
                    bestYaw = candidateYaw;
                    bestLift = probe.lift();
                }
            }
        }
        this.cachedLocalYaw = bestYaw;
        this.cachedLocalLift = bestLift;
    }

    private LocalProbe probeDirection(float yawDegrees) {
        double probeDistance = Math.max(
                this.navigationConfig.minimumLocalProbeDistance(),
                this.mob.getBbWidth()
                        * this.navigationConfig.localProbeBodyWidths());
        double yawRadians = Math.toRadians(yawDegrees);
        double directionX = -Math.sin(yawRadians);
        double directionZ = Math.cos(yawRadians);
        AABB start = this.mob.getBoundingBox().deflate(COLLISION_EPSILON);
        int samples = 3;
        double maximumLift = Math.max(0.0, this.mob.maxUpStep());
        double liftStep = Math.max(0.5, maximumLift / 6.0);

        for (double lift = 0.0;
                lift <= maximumLift + COLLISION_EPSILON;
                lift += liftStep) {
            boolean passable = true;
            for (int sample = 1; sample <= samples; sample++) {
                double distance = probeDistance * sample / samples;
                AABB candidate = start.move(
                        directionX * distance,
                        lift,
                        directionZ * distance);
                if (!this.level.noCollision(this.mob, candidate)) {
                    passable = false;
                    break;
                }
            }
            if (passable) {
                AABB destination = start.move(
                        directionX * probeDistance,
                        lift,
                        directionZ * probeDistance);
                double supportDepth = Math.max(0.5, maximumLift + 0.25);
                if (!this.level.noCollision(
                        this.mob,
                        destination.move(0.0, -supportDepth, 0.0))) {
                    return new LocalProbe(true, lift);
                }
            }
        }
        return LocalProbe.BLOCKED;
    }

    private static double horizontalDistanceSquared(Vec3 left, Vec3 right) {
        double x = left.x - right.x;
        double z = left.z - right.z;
        return x * x + z * z;
    }

    private Vec3 calculateSteeringTarget(Path currentPath) {
        DinosaurOrientationConfig orientation =
                ((ProceduralDinosaur) this.mob).proceduralConfig().orientation();
        double requestedSpeed = this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * this.speedModifier;
        double turnRadius = orientation.turningRadiusBlocks(requestedSpeed);
        double minimumLookAhead =
                Math.max(MIN_LOOK_AHEAD_BLOCKS, this.mob.getBbWidth() * 0.5);
        double maximumLookAhead = Math.max(
                minimumLookAhead,
                orientation.maximumPathLookAheadBlocks());
        double lookAhead = Mth.clamp(
                turnRadius * orientation.pathLookAheadRadiusMultiplier(),
                minimumLookAhead,
                maximumLookAhead);

        Vec3 cursor = this.getTempMobPos();
        Vec3 result = currentPath.getNextEntityPos(this.mob);
        double remaining = lookAhead;
        int lookAheadNodes = Math.min(
                HARD_MAX_LOOK_AHEAD_NODES,
                Mth.ceil(lookAhead) + 2);
        int finalIndex = Math.min(
                currentPath.getNodeCount(),
                currentPath.getNextNodeIndex() + lookAheadNodes);
        for (int index = currentPath.getNextNodeIndex();
                index < finalIndex;
                index++) {
            Vec3 node = currentPath.getEntityPosAtNode(this.mob, index);
            if (Math.abs(node.y - cursor.y) > this.mob.maxUpStep() + 0.25) {
                break;
            }

            Vec3 segment = node.subtract(cursor);
            double segmentLength = segment.horizontalDistance();
            if (segmentLength <= 1.0E-6) {
                cursor = node;
                result = node;
                continue;
            }
            if (segmentLength >= remaining) {
                double fraction = remaining / segmentLength;
                return new Vec3(
                        Mth.lerp(fraction, cursor.x, node.x),
                        Mth.lerp(fraction, cursor.y, node.y),
                        Mth.lerp(fraction, cursor.z, node.z));
            }

            remaining -= segmentLength;
            cursor = node;
            result = node;
        }
        return result;
    }

    private boolean isSafeDirectTarget(Vec3 target) {
        Vec3 start = this.mob.position();
        double distance = start.distanceTo(target);
        double collisionSampleStep = Mth.clamp(
                this.mob.getBbWidth() * 0.25,
                MIN_COLLISION_SAMPLE_STEP,
                MAX_COLLISION_SAMPLE_STEP);
        if (distance <= collisionSampleStep) {
            return true;
        }

        AABB startBounds = this.mob.getBoundingBox();
        double supportProbeDepth = Math.max(
                0.25,
                Math.min(0.75, this.mob.maxUpStep() + 0.1));
        int samples = Mth.ceil(distance / collisionSampleStep);
        for (int sample = 1; sample <= samples; sample++) {
            double fraction = (double) sample / samples;
            double x = Mth.lerp(fraction, start.x, target.x);
            double y = Mth.lerp(fraction, start.y, target.y);
            double z = Mth.lerp(fraction, start.z, target.z);
            AABB candidate = startBounds
                    .move(x - start.x, y - start.y, z - start.z)
                    .deflate(COLLISION_EPSILON);
            if (!this.level.noCollision(this.mob, candidate)
                    || this.level.noCollision(
                            this.mob,
                            candidate.move(0.0, -supportProbeDepth, 0.0))) {
                return false;
            }
        }
        return true;
    }

    private record LocalProbe(boolean passable, double lift) {
        private static final LocalProbe BLOCKED =
                new LocalProbe(false, 0.0);
    }
}
