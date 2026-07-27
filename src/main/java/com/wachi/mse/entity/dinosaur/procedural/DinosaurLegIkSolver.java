package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic two-segment leg solver shared by client and logical code.
 *
 * <p>Most terrain variation is absorbed by the knees. A small body pitch or
 * roll can be supplied for extreme compression; targets are transformed back
 * through that body rotation before each three-dimensional IK solve.
 */
public final class DinosaurLegIkSolver {
    private static final double EPSILON = 1.0E-6;
    private static final int BODY_HEIGHT_COARSE_STEPS = 32;
    private static final int BODY_HEIGHT_REFINEMENT_STEPS = 12;
    private static final double BODY_HEIGHT_NEUTRAL_BIAS = 1.0E-7;

    private DinosaurLegIkSolver() {
    }

    public static float calculateBodyTranslationY(
            DinosaurProceduralConfig config,
            List<DinosaurTerrainSample> samples,
            float bodyPitchRadians,
            float bodyRollRadians) {
        boolean hasConstraint = samples.stream().anyMatch(
                sample -> sample.supportWeight() > EPSILON
                        && canEverReachWithBodyCorrection(config, sample));
        if (!hasConstraint || config.maxBodyVerticalCorrection() <= EPSILON) {
            return 0.0F;
        }

        double limit = config.maxBodyVerticalCorrection();
        double step = limit * 2.0 / BODY_HEIGHT_COARSE_STEPS;
        double bestHeight = 0.0;
        double bestCost = bodyHeightCost(
                config,
                samples,
                bodyPitchRadians,
                bodyRollRadians,
                bestHeight);

        for (int index = 0; index <= BODY_HEIGHT_COARSE_STEPS; index++) {
            double candidate = -limit + index * step;
            double cost = bodyHeightCost(
                    config,
                    samples,
                    bodyPitchRadians,
                    bodyRollRadians,
                    candidate);
            if (cost < bestCost) {
                bestCost = cost;
                bestHeight = candidate;
            }
        }

        for (int iteration = 0; iteration < BODY_HEIGHT_REFINEMENT_STEPS; iteration++) {
            step *= 0.5;
            double lower = Mth.clamp(bestHeight - step, -limit, limit);
            double upper = Mth.clamp(bestHeight + step, -limit, limit);
            double lowerCost = bodyHeightCost(
                    config,
                    samples,
                    bodyPitchRadians,
                    bodyRollRadians,
                    lower);
            double upperCost = bodyHeightCost(
                    config,
                    samples,
                    bodyPitchRadians,
                    bodyRollRadians,
                    upper);
            if (lowerCost < bestCost) {
                bestCost = lowerCost;
                bestHeight = lower;
            }
            if (upperCost < bestCost) {
                bestCost = upperCost;
                bestHeight = upper;
            }
        }

        return (float) bestHeight;
    }

    public static List<DinosaurLegPose> solve(
            DinosaurProceduralConfig config,
            List<DinosaurTerrainSample> samples,
            DinosaurGaitState gait,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians) {
        Map<String, DinosaurTerrainSample> byLegId = new HashMap<>();
        for (DinosaurTerrainSample sample : samples) {
            byLegId.put(sample.legId(), sample);
        }

        List<DinosaurLegPose> result = new ArrayList<>(config.legs().size());
        for (DinosaurLegRig rig : config.legs()) {
            DinosaurTerrainSample sample = byLegId.get(rig.id());
            if (sample != null) {
                result.add(solveLeg(
                        config,
                        rig,
                        sample,
                        gait,
                        bodyTranslationY,
                        bodyPitchRadians,
                        bodyRollRadians));
            }
        }
        return List.copyOf(result);
    }

    private static DinosaurLegPose solveLeg(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurTerrainSample sample,
            DinosaurGaitState gait,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians) {
        double stanceTarget = stanceFootPivotHeight(config, rig, sample);
        double neutralTarget = rig.footPivotHeight();
        double swingAmount = (1.0 - sample.supportWeight()) * gait.activity();
        double swingBase = Math.max(neutralTarget, stanceTarget);
        // With no collision anywhere in the observation range, reaching for
        // the bounded lower target reads better than playing a swing pose
        // over empty space. It is exactly the same height used when a block is
        // found at the configured minimum visual drop.
        double targetHeight = sample.valid()
                ? Mth.lerp(sample.supportWeight(), swingBase, stanceTarget)
                        + config.swingFootLift() * swingAmount
                : stanceTarget;
        return solveTarget(
                config,
                rig,
                (float) targetHeight,
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians,
                sample.valid() && sample.supportWeight() >= 0.95F,
                sample.valid());
    }

    /**
     * Solves an explicit foot-pivot target. The renderer also uses this to
     * compensate vertical translation already contributed by a JSON clip.
     */
    public static DinosaurLegPose solveTarget(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            float targetHeight,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians,
            boolean planted) {
        return solveTarget(
                config,
                rig,
                targetHeight,
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians,
                planted,
                true);
    }

    /**
     * Produces the same anatomical pose as a real contact at the lowest
     * vertically reachable point. Logical support flags are supplied by the
     * caller because this method is also used to pose a leg over empty space.
     */
    public static DinosaurLegPose solveMaximumExtension(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians,
            boolean terrainContact) {
        Vec3 hipWorld = targetFromBodySpace(
                config,
                hipPosition(rig),
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians);
        double horizontalX =
                rig.renderedModelXOffset() - hipWorld.x;
        double horizontalZ = rig.modelZOffset() - hipWorld.z;
        double horizontalDistanceSquared =
                horizontalX * horizontalX + horizontalZ * horizontalZ;
        double maxReach = maximumReach(config, rig);
        double verticalReach = Math.sqrt(Math.max(
                0.0,
                maxReach * maxReach - horizontalDistanceSquared));
        float targetHeight = (float) (hipWorld.y - verticalReach);
        return solveTarget(
                config,
                rig,
                targetHeight,
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians,
                false,
                terrainContact)
                .withForcedMaximumExtension();
    }

    /**
     * Variant used when re-solving a render-smoothed leg. Terrain contact
     * changes only the logical support flags: every pose uses the same
     * anatomical reach limits.
     */
    public static DinosaurLegPose solveTarget(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            float targetHeight,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians,
            boolean planted,
            boolean terrainContact) {
        double upperLength = rig.upperLength();
        double lowerLength = rig.lowerLength();
        double minReach = minimumReach(config, rig);
        double maxReach = maximumReach(config, rig);
        Vec3 hip = hipPosition(rig);
        Vec3 target = targetInBodySpace(
                config,
                rig,
                targetHeight,
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians);
        Vec3 requestedOffset = target.subtract(hip);
        double requestedReach = requestedOffset.length();
        boolean targetBelowHip = target.y < hip.y - EPSILON;
        boolean reachable = targetBelowHip
                && requestedReach >= minReach - EPSILON
                && requestedReach <= maxReach + EPSILON;
        double solvedReach = Mth.clamp(requestedReach, minReach, maxReach);
        Vec3 direction = safeLegDirection(requestedOffset, targetBelowHip);

        double along = (upperLength * upperLength
                        - lowerLength * lowerLength
                        + solvedReach * solvedReach)
                / (2.0 * solvedReach);
        double perpendicularDistance = Math.sqrt(Math.max(
                0.0,
                upperLength * upperLength - along * along));
        Vec3 preferredBend = new Vec3(0.0, 0.0, rig.kneeBendDirection());
        Vec3 perpendicular = preferredBend.subtract(
                direction.scale(preferredBend.dot(direction)));
        if (perpendicular.lengthSqr() <= EPSILON) {
            perpendicular = new Vec3(rig.kneeBendDirection(), 0.0, 0.0)
                    .subtract(direction.scale(
                            direction.x * rig.kneeBendDirection()));
        }
        perpendicular = perpendicular.normalize();

        Vec3 knee = hip.add(direction.scale(along))
                .add(perpendicular.scale(perpendicularDistance));
        Vec3 solvedFoot = hip.add(direction.scale(solvedReach));
        Vec3 upperVector = knee.subtract(hip);
        Vec3 lowerVector = solvedFoot.subtract(knee);
        DinosaurBoneRotation hipRotation = rotationFromDownVector(upperVector);
        Matrix3 hipMatrix = Matrix3.fromEuler(hipRotation);
        DinosaurBoneRotation kneeRotation =
                rotationFromDownVector(hipMatrix.transpose().transform(lowerVector));
        Matrix3 bodyMatrix = Matrix3.fromEuler(new DinosaurBoneRotation(
                bodyPitchRadians,
                0.0F,
                bodyRollRadians));
        Matrix3 legWorldMatrix = bodyMatrix
                .multiply(hipMatrix)
                .multiply(Matrix3.fromEuler(kneeRotation));
        DinosaurBoneRotation footRotation =
                legWorldMatrix.transpose().toEuler();
        Vec3 solvedFootWorld = targetFromBodySpace(
                config,
                solvedFoot,
                bodyTranslationY,
                bodyPitchRadians,
                bodyRollRadians);
        return new DinosaurLegPose(
                rig.id(),
                hipRotation,
                kneeRotation,
                footRotation,
                targetHeight,
                (float) solvedFootWorld.y,
                (float) (solvedReach / (upperLength + lowerLength)),
                terrainContact,
                planted && reachable,
                reachable,
                false);
    }

    private static double bodyHeightCost(
            DinosaurProceduralConfig config,
            List<DinosaurTerrainSample> samples,
            float bodyPitchRadians,
            float bodyRollRadians,
            double bodyTranslationY) {
        double cost = bodyTranslationY * bodyTranslationY * BODY_HEIGHT_NEUTRAL_BIAS;
        for (DinosaurTerrainSample sample : samples) {
            if (sample.supportWeight() <= EPSILON
                    || !canEverReachWithBodyCorrection(config, sample)) {
                continue;
            }

            DinosaurLegRig rig = config.leg(sample.legId());
            Vec3 target = targetInBodySpace(
                    config,
                    rig,
                    stanceFootPivotHeight(config, rig, sample),
                    bodyTranslationY,
                    bodyPitchRadians,
                    bodyRollRadians);
            double reach = target.distanceTo(hipPosition(rig));
            double violation = 0.0;
            if (reach < minimumReach(config, rig)) {
                violation = minimumReach(config, rig) - reach;
            } else if (reach > maximumReach(config, rig)) {
                violation = reach - maximumReach(config, rig);
            }
            cost += violation * violation * sample.supportWeight();
        }
        return cost;
    }

    /**
     * Distant observed ground may describe the terrain slope but must not pull
     * the shared body height away from feet that can genuinely support the
     * animal. This inexpensive interval test asks whether some allowed body
     * correction could place the target inside the leg's reach.
     */
    private static boolean canEverReachWithBodyCorrection(
            DinosaurProceduralConfig config,
            DinosaurTerrainSample sample) {
        if (!sample.valid()) {
            return false;
        }
        DinosaurLegRig rig = config.leg(sample.legId());
        double verticalReachAtNeutralBody = rig.hipHeight()
                - stanceFootPivotHeight(config, rig, sample);
        double minimumPossibleReach = verticalReachAtNeutralBody
                - config.maxBodyVerticalCorrection();
        double maximumPossibleReach = verticalReachAtNeutralBody
                + config.maxBodyVerticalCorrection();
        return maximumPossibleReach >= minimumReach(config, rig) - EPSILON
                && minimumPossibleReach <= maximumReach(config, rig) + EPSILON;
    }

    private static Vec3 hipPosition(DinosaurLegRig rig) {
        return new Vec3(
                rig.renderedModelXOffset(),
                rig.hipHeight(),
                rig.modelZOffset());
    }

    private static Vec3 targetInBodySpace(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            double targetHeight,
            double bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians) {
        Vec3 pivot = new Vec3(0.0, config.bodyPivotHeight(), 0.0);
        Vec3 translatedTarget = new Vec3(
                rig.renderedModelXOffset(),
                targetHeight - bodyTranslationY,
                rig.modelZOffset());
        Vec3 relative = translatedTarget.subtract(pivot);
        return pivot.add(
                Matrix3.fromEuler(new DinosaurBoneRotation(
                                bodyPitchRadians,
                                0.0F,
                                bodyRollRadians))
                        .transpose()
                        .transform(relative));
    }

    private static Vec3 targetFromBodySpace(
            DinosaurProceduralConfig config,
            Vec3 bodySpacePoint,
            double bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians) {
        Vec3 pivot = new Vec3(0.0, config.bodyPivotHeight(), 0.0);
        Vec3 rotated = Matrix3.fromEuler(new DinosaurBoneRotation(
                        bodyPitchRadians,
                        0.0F,
                        bodyRollRadians))
                .transform(bodySpacePoint.subtract(pivot));
        return pivot.add(rotated).add(0.0, bodyTranslationY, 0.0);
    }

    private static Vec3 safeLegDirection(Vec3 requestedOffset, boolean targetBelowHip) {
        if (requestedOffset.lengthSqr() <= EPSILON) {
            return new Vec3(0.0, -1.0, 0.0);
        }
        Vec3 direction = requestedOffset.normalize();
        if (targetBelowHip) {
            return direction;
        }
        return new Vec3(direction.x, Math.min(direction.y, -0.05), direction.z)
                .normalize();
    }

    private static DinosaurBoneRotation rotationFromDownVector(Vec3 vector) {
        Vec3 direction = vector.normalize();
        double horizontalYLength =
                Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        return new DinosaurBoneRotation(
                (float) Math.atan2(-direction.z, horizontalYLength),
                0.0F,
                (float) Math.atan2(direction.x, -direction.y));
    }

    private static double stanceFootPivotHeight(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurTerrainSample sample) {
        return sample.heightOffset()
                + rig.footPivotHeight()
                - config.footContactHeight();
    }

    private static double minimumReach(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig) {
        return Math.max(
                Math.abs(rig.upperLength() - rig.lowerLength()) + EPSILON,
                (rig.upperLength() + rig.lowerLength())
                        * config.minLegReachFraction());
    }

    private static double maximumReach(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig) {
        return maximumReach(rig, config.maxLegReachFraction());
    }

    private static double maximumReach(
            DinosaurLegRig rig,
            float reachFraction) {
        return (rig.upperLength() + rig.lowerLength()) * reachFraction;
    }

    private static float wrapRadians(double angle) {
        while (angle <= -Math.PI) {
            angle += Math.PI * 2.0;
        }
        while (angle > Math.PI) {
            angle -= Math.PI * 2.0;
        }
        return (float) angle;
    }

    private record Matrix3(
            double m00,
            double m01,
            double m02,
            double m10,
            double m11,
            double m12,
            double m20,
            double m21,
            double m22) {
        static Matrix3 fromEuler(DinosaurBoneRotation rotation) {
            double x = rotation.xRadians();
            double y = rotation.yRadians();
            double z = rotation.zRadians();
            double sinX = Math.sin(x);
            double cosX = Math.cos(x);
            double sinY = Math.sin(y);
            double cosY = Math.cos(y);
            double sinZ = Math.sin(z);
            double cosZ = Math.cos(z);
            return new Matrix3(
                    cosZ * cosY,
                    cosZ * sinY * sinX - sinZ * cosX,
                    cosZ * sinY * cosX + sinZ * sinX,
                    sinZ * cosY,
                    sinZ * sinY * sinX + cosZ * cosX,
                    sinZ * sinY * cosX - cosZ * sinX,
                    -sinY,
                    cosY * sinX,
                    cosY * cosX);
        }

        Matrix3 multiply(Matrix3 right) {
            return new Matrix3(
                    this.m00 * right.m00 + this.m01 * right.m10 + this.m02 * right.m20,
                    this.m00 * right.m01 + this.m01 * right.m11 + this.m02 * right.m21,
                    this.m00 * right.m02 + this.m01 * right.m12 + this.m02 * right.m22,
                    this.m10 * right.m00 + this.m11 * right.m10 + this.m12 * right.m20,
                    this.m10 * right.m01 + this.m11 * right.m11 + this.m12 * right.m21,
                    this.m10 * right.m02 + this.m11 * right.m12 + this.m12 * right.m22,
                    this.m20 * right.m00 + this.m21 * right.m10 + this.m22 * right.m20,
                    this.m20 * right.m01 + this.m21 * right.m11 + this.m22 * right.m21,
                    this.m20 * right.m02 + this.m21 * right.m12 + this.m22 * right.m22);
        }

        Matrix3 transpose() {
            return new Matrix3(
                    this.m00,
                    this.m10,
                    this.m20,
                    this.m01,
                    this.m11,
                    this.m21,
                    this.m02,
                    this.m12,
                    this.m22);
        }

        Vec3 transform(Vec3 vector) {
            return new Vec3(
                    this.m00 * vector.x + this.m01 * vector.y + this.m02 * vector.z,
                    this.m10 * vector.x + this.m11 * vector.y + this.m12 * vector.z,
                    this.m20 * vector.x + this.m21 * vector.y + this.m22 * vector.z);
        }

        DinosaurBoneRotation toEuler() {
            double y = Math.asin(Mth.clamp(-this.m20, -1.0, 1.0));
            double cosY = Math.cos(y);
            double x;
            double z;
            if (Math.abs(cosY) > EPSILON) {
                x = Math.atan2(this.m21, this.m22);
                z = Math.atan2(this.m10, this.m00);
            } else {
                x = Math.atan2(-this.m12, this.m11);
                z = 0.0;
            }
            return new DinosaurBoneRotation(
                    wrapRadians(x),
                    wrapRadians(y),
                    wrapRadians(z));
        }
    }
}
