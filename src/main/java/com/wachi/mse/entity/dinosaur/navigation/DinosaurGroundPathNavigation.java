package com.wachi.mse.entity.dinosaur.navigation;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import net.minecraft.util.Mth;
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
 * <p>The A* path remains Minecraft's collision-aware grid path. Its exact
 * nodes are converted into a short pure-pursuit target whose look-ahead is
 * derived from the species' turning radius and current path speed. Unsafe
 * corner cuts fall back to the vanilla next node.</p>
 */
public final class DinosaurGroundPathNavigation extends GroundPathNavigation {
    private static final int HARD_MAX_LOOK_AHEAD_NODES = 64;
    private static final double MIN_LOOK_AHEAD_BLOCKS = 0.75;
    private static final double MIN_COLLISION_SAMPLE_STEP = 0.2;
    private static final double MAX_COLLISION_SAMPLE_STEP = 0.75;
    private static final double COLLISION_EPSILON = 1.0E-4;

    public DinosaurGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
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
}
