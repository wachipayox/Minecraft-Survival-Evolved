package com.wachi.mse.entity.dinosaur.navigation;

import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import java.util.function.Supplier;

/**
 * A* cost model that favors paths a non-pivoting animal can follow.
 *
 * <p>Minecraft still owns collision, step and hazard evaluation. This class
 * only adds the approximate arc length required to change heading, including
 * the first edge relative to the dinosaur's current body orientation. Long,
 * smooth approaches consequently beat grid paths made of abrupt zigzags.</p>
 */
final class DinosaurPathFinder extends PathFinder {
    private static final double VECTOR_EPSILON = 1.0E-8;

    private final Mob mob;
    private final Supplier<DinosaurOrientationConfig> orientation;

    DinosaurPathFinder(
            NodeEvaluator nodeEvaluator,
            int maxVisitedNodes,
            Mob mob,
            Supplier<DinosaurOrientationConfig> orientation) {
        super(nodeEvaluator, maxVisitedNodes);
        this.mob = mob;
        this.orientation = orientation;
    }

    @Override
    protected float distance(Node first, Node second) {
        float traversalCost = super.distance(first, second);
        double incomingX;
        double incomingZ;
        if (first.cameFrom == null) {
            double yawRadians = Math.toRadians(this.mob.getYRot());
            incomingX = -Math.sin(yawRadians);
            incomingZ = Math.cos(yawRadians);
        } else {
            incomingX = first.x - first.cameFrom.x;
            incomingZ = first.z - first.cameFrom.z;
        }

        double outgoingX = second.x - first.x;
        double outgoingZ = second.z - first.z;
        double incomingLength =
                Math.sqrt(incomingX * incomingX + incomingZ * incomingZ);
        double outgoingLength =
                Math.sqrt(outgoingX * outgoingX + outgoingZ * outgoingZ);
        if (incomingLength <= VECTOR_EPSILON
                || outgoingLength <= VECTOR_EPSILON) {
            return traversalCost;
        }

        double cosine = Mth.clamp(
                (incomingX * outgoingX + incomingZ * outgoingZ)
                        / (incomingLength * outgoingLength),
                -1.0,
                1.0);
        double turnRadians = Math.acos(cosine);
        double nominalSpeed =
                this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double turnRadius =
                this.orientation.get().turningRadiusBlocks(nominalSpeed);
        return traversalCost + (float) (turnRadius * turnRadians);
    }
}
