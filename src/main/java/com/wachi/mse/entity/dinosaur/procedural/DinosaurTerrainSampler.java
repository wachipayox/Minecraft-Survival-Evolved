package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes a terrain pose without consulting goals, navigation, NoAI or the
 * entity's cached on-ground flag.
 */
public final class DinosaurTerrainSampler {
    private static final double SAMPLE_EPSILON = 1.0E-5;
    private static final double FOOTPRINT_SUPPORT_DEPTH = 0.125;
    private static final double FOOTPRINT_SUPPORT_TOP_TOLERANCE = 0.05;
    private static final double FOOT_PLANE_CENTER_TOLERANCE = 0.20;

    private DinosaurTerrainSampler() {
    }

    /**
     * Uses interpolated transform data for a render frame. Terrain evaluation
     * itself is identical to {@link #sampleAuthoritative}.
     */
    public static DinosaurProceduralPose sampleInterpolated(
            LivingEntity entity,
            DinosaurProceduralConfig config,
            float partialTick) {
        Vec3 origin = new Vec3(
                Mth.lerp((double) partialTick, entity.xo, entity.getX()),
                Mth.lerp((double) partialTick, entity.yo, entity.getY()),
                Mth.lerp((double) partialTick, entity.zo, entity.getZ()));
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float headPitch = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
        DinosaurGaitState gait =
                DinosaurGaitState.sampleInterpolated(entity, config, partialTick);
        return sampleAt(
                entity,
                config,
                origin,
                bodyYaw,
                gait,
                orientationPose(config, bodyYaw, headYaw, headPitch));
    }

    /**
     * Entry point for logical/server code that needs the terrain-derived body
     * orientation at the entity's authoritative tick position.
     */
    public static DinosaurProceduralPose sampleAuthoritative(
            LivingEntity entity,
            DinosaurProceduralConfig config) {
        /*
         * LivingEntityRenderer rotates the model from yBodyRot, not from the
         * entity's navigation yaw. These values can intentionally differ
         * while a dinosaur is turning in place or steering through a tight
         * arc. Using getYRot() here made the logical bone boxes describe a
         * different rigid body than the visible model precisely during those
         * turns, which then produced an incorrect platform displacement.
         */
        float bodyYaw = entity.yBodyRot;
        return sampleAt(
                entity,
                config,
                entity.position(),
                bodyYaw,
                DinosaurGaitState.sampleAuthoritative(entity, config),
                orientationPose(
                        config,
                        bodyYaw,
                        entity.yHeadRot,
                        entity.getXRot()));
    }

    /**
     * Deterministic core shared by client and server. It does not mutate the
     * entity and does not force chunk loads.
     */
    public static DinosaurProceduralPose sampleAt(
            LivingEntity entity,
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees) {
        return sampleAt(
                entity,
                config,
                origin,
                bodyYawDegrees,
                DinosaurGaitState.sampleAuthoritative(entity, config),
                orientationPose(
                        config,
                        bodyYawDegrees,
                        entity.yHeadRot,
                        entity.getXRot()));
    }

    /**
     * Deterministic core with an explicit gait state for tests and logical
     * callers that already captured movement data.
     */
    public static DinosaurProceduralPose sampleAt(
            LivingEntity entity,
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees,
            DinosaurGaitState gait) {
        return sampleAt(
                entity,
                config,
                origin,
                bodyYawDegrees,
                gait,
                orientationPose(
                        config,
                        bodyYawDegrees,
                        entity.yHeadRot,
                        entity.getXRot()));
    }

    private static DinosaurProceduralPose sampleAt(
            LivingEntity entity,
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees,
            DinosaurGaitState gait,
            DinosaurOrientationPose orientation) {
        Level level = entity.level();
        double modelYawRadians = Math.toRadians(180.0F - bodyYawDegrees);
        double sinYaw = Math.sin(modelYawRadians);
        double cosYaw = Math.cos(modelYawRadians);
        if (isDescendingAirborne(entity)) {
            return sampleAirbornePose(
                    config,
                    origin,
                    bodyYawDegrees,
                    gait,
                    orientation,
                    sinYaw,
                    cosYaw);
        }
        CollisionContext collisionContext = CollisionContext.of(entity);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        List<DinosaurTerrainSample> samples = new ArrayList<>(config.legs().size());

        for (DinosaurLegRig leg : config.legs()) {
            double observationBelow = observationDepthBelow(config, leg);
            FootGroundFit groundFit = sampleFootGround(
                    level,
                    collisionContext,
                    origin,
                    leg,
                    config,
                    sinYaw,
                    cosYaw,
                    config.sampleAbove(),
                    observationBelow);
            boolean valid = groundFit != null;
            Vec3 position;
            if (valid) {
                position = groundFit.position();
            } else {
                // Keep the old bounded visual drop when no floor exists at
                // all. The farther awareness range is only allowed to affect
                // the pose when it finds real collision geometry.
                Vec3 nominalPosition = modelPointToWorld(
                        origin,
                        leg.modelFootCenterXOffset(),
                        leg.modelFootCenterZOffset(),
                        sinYaw,
                        cosYaw);
                position = new Vec3(
                        nominalPosition.x,
                        origin.y - config.sampleBelow(),
                        nominalPosition.z);
            }
            float supportWeight = gait.supportWeight(leg.id());
            FootTilt footTilt = valid && supportWeight > SAMPLE_EPSILON
                    ? groundFit.tilt()
                    : FootTilt.NONE;
            DinosaurTerrainSample sample = new DinosaurTerrainSample(
                    leg.id(),
                    leg.shortName(),
                    position,
                    position.y - origin.y,
                    valid,
                    supportWeight,
                    footTilt.pitchRadians(),
                    footTilt.rollRadians());
            samples.add(sample);
        }

        boolean hasRealSupport = samples.stream().anyMatch(
                sample -> sample.valid() && sample.supportWeight() > SAMPLE_EPSILON);
        SlopeEstimate slope = estimateTerrainSlope(config, samples, hasRealSupport);
        float terrainPitch = slope.pitchRadians();
        float terrainRoll = slope.rollRadians();
        float levelBodyTranslationY = hasRealSupport
                ? DinosaurLegIkSolver.calculateBodyTranslationY(
                        config,
                        samples,
                        0.0F,
                        0.0F)
                : 0.0F;
        List<DinosaurLegPose> levelLegs = DinosaurLegIkSolver.solve(
                config,
                samples,
                levelBodyTranslationY,
                0.0F,
                0.0F);
        float bodyPitch = calculateHybridPitch(config, terrainPitch, levelLegs);
        float bodyRoll = calculateHybridRoll(config, terrainRoll, levelLegs);
        float bodyTranslationY = hasRealSupport
                ? DinosaurLegIkSolver.calculateBodyTranslationY(
                        config,
                        samples,
                        bodyPitch,
                        bodyRoll)
                : 0.0F;
        List<DinosaurLegPose> legs = DinosaurLegIkSolver.solve(
                config,
                samples,
                bodyTranslationY,
                bodyPitch,
                bodyRoll);
        float balancePitch = 0.0F;
        float balanceRoll = 0.0F;
        DinosaurStabilityAssessment stability = DinosaurStabilitySolver.assess(
                config,
                origin,
                bodyYawDegrees,
                samples,
                legs,
                DinosaurFootprintSupport.none());
        if (stability.requiresRecovery()) {
            DinosaurFootprintSupport footprintSupport =
                    sampleFootprintSupport(
                            entity,
                            origin,
                            collisionContext,
                            mutableBlockPos);
            stability = DinosaurStabilitySolver.assess(
                    config,
                    origin,
                    bodyYawDegrees,
                    samples,
                    legs,
                    footprintSupport);
            if (stability.requiresRecovery()) {
                RecoveryLean recoveryLean = calculateRecoveryLean(
                        config,
                        stability,
                        bodyYawDegrees);
                boolean terrainSupportLost =
                        samples.stream().anyMatch(sample -> !sample.valid())
                                || legs.stream().anyMatch(
                                        leg -> !leg.reachable());
                boolean locomotionBalance =
                        gait.activity()
                                        > config.stability()
                                                .maximumActivityForStaticBalance()
                                && !terrainSupportLost;
                float recoveredPitch = Mth.clamp(
                        bodyPitch + recoveryLean.pitchRadians(),
                        -config.maxHybridPitchRadians(),
                        config.maxHybridPitchRadians());
                float recoveredRoll = Mth.clamp(
                        bodyRoll + recoveryLean.rollRadians(),
                        -config.maxHybridRollRadians(),
                        config.maxHybridRollRadians());
                if (locomotionBalance) {
                    // A gait can intentionally leave only one side in stance.
                    // Keep that natural torso weight shift, but do not feed it
                    // back into the structural leg solve: otherwise all hips
                    // rotate towards the centre of mass and the descending
                    // feet are pulled diagonally off their authored path.
                    balancePitch = recoveredPitch - bodyPitch;
                    balanceRoll = recoveredRoll - bodyRoll;
                    bodyPitch = recoveredPitch;
                    bodyRoll = recoveredRoll;
                } else {
                    bodyPitch = recoveredPitch;
                    bodyRoll = recoveredRoll;
                    bodyTranslationY = hasRealSupport
                            ? DinosaurLegIkSolver.calculateBodyTranslationY(
                                    config,
                                    samples,
                                    bodyPitch,
                                    bodyRoll)
                            : 0.0F;
                    legs = DinosaurLegIkSolver.solve(
                            config,
                            samples,
                            bodyTranslationY,
                            bodyPitch,
                            bodyRoll);
                    stability = DinosaurStabilitySolver.assess(
                            config,
                            origin,
                            bodyYawDegrees,
                            samples,
                            legs,
                            footprintSupport);
                    legs = extendTerrainLostLegsForRecovery(
                            config,
                            legs,
                            bodyTranslationY,
                            bodyPitch,
                            bodyRoll);
                }
            }
        }

        return new DinosaurProceduralPose(
                origin,
                bodyYawDegrees,
                orientation,
                bodyPitch,
                bodyRoll,
                bodyTranslationY,
                bodyPitch,
                bodyRoll,
                bodyTranslationY,
                balancePitch,
                balanceRoll,
                balancePitch,
                balanceRoll,
                terrainPitch,
                terrainRoll,
                slope.pitchResolved(),
                slope.rollResolved(),
                false,
                gait,
                samples,
                legs,
                stability);
    }

    private static boolean isDescendingAirborne(LivingEntity entity) {
        return !entity.onGround()
                && !entity.isInWater()
                && entity.getDeltaMovement().y < -0.01;
    }

    private static DinosaurProceduralPose sampleAirbornePose(
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees,
            DinosaurGaitState gait,
            DinosaurOrientationPose orientation,
            double sinYaw,
            double cosYaw) {
        List<DinosaurTerrainSample> samples =
                new ArrayList<>(config.legs().size());
        List<DinosaurLegPose> legs =
                new ArrayList<>(config.legs().size());
        for (DinosaurLegRig leg : config.legs()) {
            Vec3 nominalPosition = modelPointToWorld(
                    origin,
                    leg.modelFootCenterXOffset(),
                    leg.modelFootCenterZOffset(),
                    sinYaw,
                    cosYaw);
            samples.add(new DinosaurTerrainSample(
                    leg.id(),
                    leg.shortName(),
                    nominalPosition,
                    0.0,
                    false,
                    0.0F,
                    0.0F,
                    0.0F));
            legs.add(DinosaurLegIkSolver.solveAirborneRetraction(
                    config,
                    leg,
                    0.0F,
                    0.0F,
                    0.0F));
        }
        DinosaurStabilityAssessment stability =
                DinosaurStabilityAssessment.notEvaluable(
                        origin,
                        0,
                        DinosaurFootprintSupport.none());
        return new DinosaurProceduralPose(
                origin,
                bodyYawDegrees,
                orientation,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                false,
                true,
                gait,
                samples,
                legs,
                stability);
    }

    private static List<DinosaurLegPose> extendTerrainLostLegsForRecovery(
            DinosaurProceduralConfig config,
            List<DinosaurLegPose> legs,
            float bodyTranslationY,
            float bodyPitchRadians,
            float bodyRollRadians) {
        List<DinosaurLegPose> result = new ArrayList<>(legs.size());
        for (DinosaurLegPose leg : legs) {
            if (leg.terrainContact()
                    && !leg.reachStatus().needsMaximumExtension()) {
                result.add(leg);
                continue;
            }

            DinosaurLegPose extended =
                    DinosaurLegIkSolver.solveMaximumExtension(
                            config,
                            config.leg(leg.legId()),
                            bodyTranslationY,
                            bodyPitchRadians,
                            bodyRollRadians,
                            leg.terrainContact());
            result.add(extended.withRequestedState(leg));
        }
        return List.copyOf(result);
    }

    /**
     * Terrain fitting describes real ground only. Once balance is already
     * known to be lost, this separate response leans the body toward the
     * authoritative fall direction without allowing missing samples to
     * masquerade as a terrain plane. The configured hybrid angles are hard
     * anatomical limits, not recovery amplitudes: the actual correction is
     * derived from how far the centre of mass lies beyond the tolerated
     * support boundary and the dinosaur's scale-aware body height.
     */
    private static RecoveryLean calculateRecoveryLean(
            DinosaurProceduralConfig config,
            DinosaurStabilityAssessment stability,
            float bodyYawDegrees) {
        Vec3 fallDirection = stability.fallDirectionWorld();
        double horizontalLengthSquared =
                fallDirection.x * fallDirection.x
                        + fallDirection.z * fallDirection.z;
        if (horizontalLengthSquared <= SAMPLE_EPSILON) {
            return RecoveryLean.NONE;
        }

        double signedMargin = stability.signedMarginBlocks();
        if (!Double.isFinite(signedMargin)) {
            // With no support polygon there is no meaningful corrective
            // posture. Maximum-extension legs and the authoritative fall
            // controller handle the unsupported state; forcing the torso to
            // its angular limit here only creates a visual snap.
            return RecoveryLean.NONE;
        }
        double outsideDistance = Math.max(
                0.0,
                -signedMargin
                        - config.stability().toleratedOutsideDistance());
        if (outsideDistance <= SAMPLE_EPSILON) {
            return RecoveryLean.NONE;
        }
        double bodyHeight = Math.max(
                SAMPLE_EPSILON,
                config.bodyPivotHeight() - config.footContactHeight());
        double requiredAngle = Math.atan2(outsideDistance, bodyHeight);

        double inverseLength = 1.0 / Math.sqrt(horizontalLengthSquared);
        double worldX = fallDirection.x * inverseLength;
        double worldZ = fallDirection.z * inverseLength;
        double modelYawRadians = Math.toRadians(180.0F - bodyYawDegrees);
        double sinYaw = Math.sin(modelYawRadians);
        double cosYaw = Math.cos(modelYawRadians);
        double renderedModelX = cosYaw * worldX - sinYaw * worldZ;
        double modelZ = sinYaw * worldX + cosYaw * worldZ;
        return new RecoveryLean(
                (float) (modelZ * requiredAngle),
                (float) (-renderedModelX * requiredAngle));
    }

    private static DinosaurOrientationPose orientationPose(
            DinosaurProceduralConfig config,
            float bodyYawDegrees,
            float headYawDegrees,
            float headPitchDegrees) {
        float relativeYawDegrees = Mth.clamp(
                Mth.wrapDegrees(headYawDegrees - bodyYawDegrees),
                -config.orientation().maxNeckYawDegrees(),
                config.orientation().maxNeckYawDegrees());
        // DinosaurEntity stores visual/GeckoLib pitch: positive is up. This
        // is intentionally the inverse of vanilla's look-vector convention.
        float pitchDegrees = Mth.clamp(
                headPitchDegrees,
                -config.orientation().maxPitchDownDegrees(),
                config.orientation().maxPitchUpDegrees());
        float yawRadians = (float) Math.toRadians(relativeYawDegrees);
        float pitchRadians = (float) Math.toRadians(pitchDegrees);
        return new DinosaurOrientationPose(
                yawRadians,
                pitchRadians,
                yawRadians,
                pitchRadians);
    }

    /**
     * A leg first gets its complete reachable vertical range, including the
     * shared body-height correction. It then observes an additional
     * species-configured number of its own lengths. This scales naturally for
     * short, long and asymmetric legs without treating distant ground as
     * reachable support.
     */
    private static double observationDepthBelow(
            DinosaurProceduralConfig config,
            DinosaurLegRig leg) {
        double maximumReach =
                leg.totalLength() * config.maxLegReachFraction();
        double physicallyReachableDrop = Math.max(
                0.0,
                maximumReach
                        - leg.hipHeight()
                        + leg.footPivotHeight()
                        + config.maxBodyVerticalCorrection());
        double adaptiveDepth = physicallyReachableDrop
                + leg.totalLength()
                        * config.stability().awarenessBeyondReachLegLengths();
        return Math.max(config.sampleBelow(), adaptiveDepth);
    }

    /**
     * The centre ray is the sole source of contact truth. Extra probes may
     * lift a rigid sole over geometry it genuinely overlaps, but they can
     * neither invent support beside the foot nor keep a previous contact
     * alive. A symmetric inner cross supplies visual tilt only when it agrees
     * with the centre, avoiding unstable planes across unrelated voxel steps.
     */
    private static FootGroundFit sampleFootGround(
            Level level,
            CollisionContext collisionContext,
            Vec3 origin,
            DinosaurLegRig leg,
            DinosaurProceduralConfig config,
            double sinYaw,
            double cosYaw,
            double sampleAbove,
            double sampleBelow) {
        Vec3 center = modelPointToWorld(
                origin,
                leg.modelFootCenterXOffset(),
                leg.modelFootCenterZOffset(),
                sinYaw,
                cosYaw);
        Vec3 lateralAxis = new Vec3(cosYaw, 0.0, -sinYaw);
        Vec3 longitudinalAxis = new Vec3(sinYaw, 0.0, cosYaw);
        double maximumY = origin.y + sampleAbove;
        double minimumY = origin.y - sampleBelow;
        Vec3 centerHit = findGroundNearHeight(
                level,
                collisionContext,
                center,
                maximumY,
                minimumY);
        if (centerHit == null) {
            return null;
        }

        double lateralExtent = leg.footHalfWidth() * 0.95;
        double longitudinalExtent = leg.footHalfLength() * 0.95;
        int lateralSamples = footSampleCount(
                leg.footHalfWidth() * 2.0,
                config.contactPatchRadius());
        int longitudinalSamples = footSampleCount(
                leg.footHalfLength() * 2.0,
                config.contactPatchRadius());
        List<FootSurfaceSample> surfaceSamples =
                new ArrayList<>(lateralSamples * longitudinalSamples + 1);
        surfaceSamples.add(new FootSurfaceSample(0.0, 0.0, centerHit.y));

        for (int lateralIndex = 0;
                lateralIndex < lateralSamples;
                lateralIndex++) {
            double lateralFraction = sampleFraction(
                    lateralIndex,
                    lateralSamples);
            double localX = lateralExtent * lateralFraction;
            for (int longitudinalIndex = 0;
                    longitudinalIndex < longitudinalSamples;
                    longitudinalIndex++) {
                double longitudinalFraction = sampleFraction(
                        longitudinalIndex,
                        longitudinalSamples);
                double localZ = longitudinalExtent * longitudinalFraction;
                Vec3 point = center
                        .add(lateralAxis.scale(localX))
                        .add(longitudinalAxis.scale(localZ));
                Vec3 hit = findGroundNearHeight(
                        level,
                        collisionContext,
                        point,
                        maximumY,
                        minimumY);
                if (hit != null) {
                    surfaceSamples.add(new FootSurfaceSample(
                            localX,
                            localZ,
                            hit.y));
                }
            }
        }

        FootTilt tilt = sampleStableFootTilt(
                level,
                collisionContext,
                center,
                centerHit.y,
                lateralAxis,
                longitudinalAxis,
                leg,
                maximumY,
                minimumY,
                config.maxFootPitchRadians(),
                config.maxFootRollRadians());
        DinosaurRotationMath.Quaternion terrain =
                DinosaurRotationMath.terrainOrientation(
                        tilt.pitchRadians(),
                        tilt.rollRadians());
        Vec3 normal = terrain.rotate(new Vec3(0.0, 1.0, 0.0));
        double lateralSlope = -normal.x / normal.y;
        double longitudinalSlope = -normal.z / normal.y;

        /*
         * Lift the selected plane until it clears every observed collision
         * point. Nearby probes affect clearance, never contact validity.
         */
        double centerHeight = Double.NEGATIVE_INFINITY;
        for (FootSurfaceSample sample : surfaceSamples) {
            centerHeight = Math.max(
                    centerHeight,
                    sample.height()
                            - lateralSlope * sample.localX()
                            - longitudinalSlope * sample.localZ());
        }
        return new FootGroundFit(
                new Vec3(center.x, centerHeight, center.z),
                tilt);
    }

    private static FootTilt sampleStableFootTilt(
            Level level,
            CollisionContext collisionContext,
            Vec3 center,
            double centerHeight,
            Vec3 lateralAxis,
            Vec3 longitudinalAxis,
            DinosaurLegRig leg,
            double maximumY,
            double minimumY,
            float maximumPitch,
            float maximumRoll) {
        double lateralProbe = leg.footHalfWidth() * 0.65;
        double longitudinalProbe = leg.footHalfLength() * 0.65;
        if (lateralProbe <= SAMPLE_EPSILON
                || longitudinalProbe <= SAMPLE_EPSILON) {
            return FootTilt.NONE;
        }

        Vec3 left = findGroundNearHeight(
                level,
                collisionContext,
                center.add(lateralAxis.scale(-lateralProbe)),
                maximumY,
                minimumY);
        Vec3 right = findGroundNearHeight(
                level,
                collisionContext,
                center.add(lateralAxis.scale(lateralProbe)),
                maximumY,
                minimumY);
        Vec3 back = findGroundNearHeight(
                level,
                collisionContext,
                center.add(longitudinalAxis.scale(-longitudinalProbe)),
                maximumY,
                minimumY);
        Vec3 front = findGroundNearHeight(
                level,
                collisionContext,
                center.add(longitudinalAxis.scale(longitudinalProbe)),
                maximumY,
                minimumY);
        if (left == null || right == null || back == null || front == null) {
            return FootTilt.NONE;
        }

        double lateralCenter = (left.y + right.y) * 0.5;
        double longitudinalCenter = (back.y + front.y) * 0.5;
        if (Math.abs(lateralCenter - centerHeight)
                        > FOOT_PLANE_CENTER_TOLERANCE
                || Math.abs(longitudinalCenter - centerHeight)
                        > FOOT_PLANE_CENTER_TOLERANCE) {
            return FootTilt.NONE;
        }

        return limitCombinedFootTilt(
                (right.y - left.y) / (lateralProbe * 2.0),
                (front.y - back.y) / (longitudinalProbe * 2.0),
                maximumPitch,
                maximumRoll);
    }

    private static int footSampleCount(
            double fullExtent,
            double maximumProbeSpacing) {
        if (maximumProbeSpacing <= SAMPLE_EPSILON) {
            return 3;
        }
        return Mth.clamp(
                (int) Math.ceil(fullExtent / maximumProbeSpacing) + 1,
                3,
                5);
    }

    private static double sampleFraction(int index, int count) {
        return count <= 1
                ? 0.0
                : -1.0 + 2.0 * index / (count - 1.0);
    }

    private static FootTilt limitCombinedFootTilt(
            double lateralSlope,
            double longitudinalSlope,
            float maximumPitch,
            float maximumRoll) {
        float roll = maximumRoll <= 0.0F
                ? 0.0F
                : (float) Mth.clamp(
                        Math.atan(lateralSlope),
                        -maximumRoll,
                        maximumRoll);
        float pitch = maximumPitch <= 0.0F
                ? 0.0F
                : (float) Mth.clamp(
                        Math.atan(-longitudinalSlope * Math.cos(roll)),
                        -maximumPitch,
                        maximumPitch);

        double normalizedPitch = maximumPitch <= 0.0F
                ? 0.0
                : pitch / maximumPitch;
        double normalizedRoll = maximumRoll <= 0.0F
                ? 0.0
                : roll / maximumRoll;
        double combined = Math.sqrt(
                normalizedPitch * normalizedPitch
                        + normalizedRoll * normalizedRoll);
        if (combined > 1.0) {
            pitch /= (float) combined;
            roll /= (float) combined;
        }
        return new FootTilt(pitch, roll);
    }

    private static Vec3 findGroundNearHeight(
            Level level,
            CollisionContext collisionContext,
            Vec3 point,
            double maximumY,
            double minimumY) {
        int blockX = Mth.floor(point.x);
        int blockZ = Mth.floor(point.z);
        if (!level.getChunkSource().hasChunk(
                blockX >> 4,
                blockZ >> 4)) {
            return null;
        }
        HitResult hit = level.clip(new ClipContext(
                new Vec3(point.x, maximumY, point.z),
                new Vec3(point.x, minimumY, point.z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                collisionContext));
        return hit.getType() == HitResult.Type.MISS
                ? null
                : hit.getLocation();
    }

    private static DinosaurFootprintSupport sampleFootprintSupport(
            LivingEntity entity,
            Vec3 origin,
            CollisionContext collisionContext,
            BlockPos.MutableBlockPos mutableBlockPos) {
        Level level = entity.level();
        Vec3 interpolationOffset = origin.subtract(entity.position());
        AABB bounds = entity.getBoundingBox().move(interpolationOffset);
        int minBlockX = Mth.floor(bounds.minX + SAMPLE_EPSILON);
        int maxBlockX = Mth.floor(bounds.maxX - SAMPLE_EPSILON);
        int minBlockZ = Mth.floor(bounds.minZ + SAMPLE_EPSILON);
        int maxBlockZ = Mth.floor(bounds.maxZ - SAMPLE_EPSILON);
        int minBlockY =
                Mth.floor(bounds.minY - FOOTPRINT_SUPPORT_DEPTH);
        int maxBlockY = Mth.floor(
                bounds.minY + FOOTPRINT_SUPPORT_TOP_TOLERANCE);
        double totalArea = 0.0;
        double weightedX = 0.0;
        double weightedY = 0.0;
        double weightedZ = 0.0;

        for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
            for (int blockZ = minBlockZ; blockZ <= maxBlockZ; blockZ++) {
                if (!level.getChunkSource().hasChunk(
                        blockX >> 4,
                        blockZ >> 4)) {
                    continue;
                }
                for (int blockY = minBlockY;
                        blockY <= maxBlockY;
                        blockY++) {
                    mutableBlockPos.set(blockX, blockY, blockZ);
                    VoxelShape shape = level
                            .getBlockState(mutableBlockPos)
                            .getCollisionShape(
                                    level,
                                    mutableBlockPos,
                                    collisionContext);
                    for (AABB box : shape.toAabbs()) {
                        double worldTop = blockY + box.maxY;
                        if (worldTop
                                        < bounds.minY
                                                - FOOTPRINT_SUPPORT_DEPTH
                                || worldTop
                                        > bounds.minY
                                                + FOOTPRINT_SUPPORT_TOP_TOLERANCE) {
                            continue;
                        }

                        double overlapMinX =
                                Math.max(bounds.minX, blockX + box.minX);
                        double overlapMaxX =
                                Math.min(bounds.maxX, blockX + box.maxX);
                        double overlapMinZ =
                                Math.max(bounds.minZ, blockZ + box.minZ);
                        double overlapMaxZ =
                                Math.min(bounds.maxZ, blockZ + box.maxZ);
                        double width = overlapMaxX - overlapMinX;
                        double depth = overlapMaxZ - overlapMinZ;
                        if (width <= SAMPLE_EPSILON
                                || depth <= SAMPLE_EPSILON) {
                            continue;
                        }

                        double area = width * depth;
                        totalArea += area;
                        weightedX +=
                                (overlapMinX + overlapMaxX) * 0.5 * area;
                        weightedY += worldTop * area;
                        weightedZ +=
                                (overlapMinZ + overlapMaxZ) * 0.5 * area;
                    }
                }
            }
        }

        if (totalArea <= SAMPLE_EPSILON) {
            return DinosaurFootprintSupport.none();
        }
        return new DinosaurFootprintSupport(
                totalArea,
                new Vec3(
                        weightedX / totalArea,
                        weightedY / totalArea,
                        weightedZ / totalArea));
    }

    private static Vec3 modelPointToWorld(
            Vec3 origin,
            double modelX,
            double modelZ,
            double sinYaw,
            double cosYaw) {
        // GeckoLib's GeometryBone baker negates Blockbench/Bedrock X.
        double renderedModelX = -modelX;
        return new Vec3(
                origin.x + cosYaw * renderedModelX + sinYaw * modelZ,
                origin.y,
                origin.z - sinYaw * renderedModelX + cosYaw * modelZ);
    }

    private static SlopeEstimate estimateTerrainSlope(
            DinosaurProceduralConfig config,
            List<DinosaurTerrainSample> samples,
            boolean hasRealSupport) {
        PlaneSlope plane = fitTerrainPlane(config, samples);
        boolean pitchResolved = hasRealSupport && plane.longitudinalResolved();
        boolean rollResolved = hasRealSupport && plane.lateralResolved();
        float pitch = pitchResolved
                ? limitedSlope(
                        -plane.longitudinalHeightPerBlock(),
                        1.0,
                        config.maxPitchRadians(),
                        config.slopeDeadzoneRadians())
                : 0.0F;
        float roll = rollResolved
                ? limitedSlope(
                        -plane.lateralHeightPerBlock(),
                        1.0,
                        config.maxRollRadians(),
                        config.slopeDeadzoneRadians())
                : 0.0F;
        return new SlopeEstimate(pitch, roll, pitchResolved, rollResolved);
    }

    /**
     * Weighted least-squares fit of {@code y = ax + bz + c} using real
     * collision contacts only. Missing samples carry a bounded visual drop,
     * but that synthetic Y must never be interpreted as terrain. A biped
     * whose feet share Z can still resolve roll; a layout with no
     * longitudinal or lateral spread simply leaves that axis unresolved.
     * Collinear diagonal supports deliberately resolve only their strongest
     * axis because one line cannot uniquely determine a two-dimensional
     * terrain plane.
     */
    private static PlaneSlope fitTerrainPlane(
            DinosaurProceduralConfig config,
            List<DinosaurTerrainSample> samples) {
        double totalWeight = 0.0;
        double weightedX = 0.0;
        double weightedZ = 0.0;
        double weightedY = 0.0;
        for (DinosaurTerrainSample sample : samples) {
            double weight = sample.supportWeight();
            if (!sample.valid() || weight <= SAMPLE_EPSILON) {
                continue;
            }
            DinosaurLegRig leg = config.leg(sample.legId());
            totalWeight += weight;
            weightedX += leg.renderedModelXOffset() * weight;
            weightedZ += leg.modelZOffset() * weight;
            weightedY += sample.position().y * weight;
        }
        if (totalWeight <= SAMPLE_EPSILON) {
            return PlaneSlope.UNRESOLVED;
        }

        double meanX = weightedX / totalWeight;
        double meanZ = weightedZ / totalWeight;
        double meanY = weightedY / totalWeight;
        double xx = 0.0;
        double zz = 0.0;
        double xz = 0.0;
        double xy = 0.0;
        double zy = 0.0;
        for (DinosaurTerrainSample sample : samples) {
            double weight = sample.supportWeight();
            if (!sample.valid() || weight <= SAMPLE_EPSILON) {
                continue;
            }
            DinosaurLegRig leg = config.leg(sample.legId());
            double x = leg.renderedModelXOffset() - meanX;
            double z = leg.modelZOffset() - meanZ;
            double y = sample.position().y - meanY;
            xx += weight * x * x;
            zz += weight * z * z;
            xz += weight * x * z;
            xy += weight * x * y;
            zy += weight * z * y;
        }

        boolean lateralSpread = xx > SAMPLE_EPSILON;
        boolean longitudinalSpread = zz > SAMPLE_EPSILON;
        double determinant = xx * zz - xz * xz;
        double determinantScale = Math.max(xx * zz, SAMPLE_EPSILON * SAMPLE_EPSILON);
        if (lateralSpread
                && longitudinalSpread
                && determinant > SAMPLE_EPSILON * determinantScale) {
            return new PlaneSlope(
                    (xy * zz - zy * xz) / determinant,
                    (zy * xx - xy * xz) / determinant,
                    true,
                    true);
        }

        if (lateralSpread && !longitudinalSpread) {
            return new PlaneSlope(xy / xx, 0.0, true, false);
        }
        if (longitudinalSpread && !lateralSpread) {
            return new PlaneSlope(0.0, zy / zz, false, true);
        }
        if (lateralSpread && longitudinalSpread) {
            return xx >= zz
                    ? new PlaneSlope(xy / xx, 0.0, true, false)
                    : new PlaneSlope(0.0, zy / zz, false, true);
        }
        return PlaneSlope.UNRESOLVED;
    }

    private static float limitedSlope(
            double heightDifference,
            double horizontalDistance,
            float limit,
            float deadzone) {
        if (horizontalDistance <= SAMPLE_EPSILON) {
            return 0.0F;
        }

        float angle = (float) Math.atan2(heightDifference, horizontalDistance);
        if (Math.abs(angle) < deadzone) {
            return 0.0F;
        }
        return Mth.clamp(angle, -limit, limit);
    }

    private static float calculateHybridPitch(
            DinosaurProceduralConfig config,
            float terrainPitch,
            List<DinosaurLegPose> levelLegs) {
        float activation = compressionActivation(
                config,
                levelLegs,
                terrainPitch,
                false);
        float adaptiveSlopeShare = Mth.lerp(
                activation,
                config.bodyTiltSlopeShare(),
                1.0F);
        return Mth.clamp(
                terrainPitch * adaptiveSlopeShare * activation,
                -config.maxHybridPitchRadians(),
                config.maxHybridPitchRadians());
    }

    private static float calculateHybridRoll(
            DinosaurProceduralConfig config,
            float terrainRoll,
            List<DinosaurLegPose> levelLegs) {
        float activation = compressionActivation(
                config,
                levelLegs,
                terrainRoll,
                true);
        float adaptiveSlopeShare = Mth.lerp(
                activation,
                config.bodyTiltSlopeShare(),
                1.0F);
        // GeckoLib negates model X, so a high anatomical left side needs
        // negative Z rotation.
        return Mth.clamp(
                -terrainRoll * adaptiveSlopeShare * activation,
                -config.maxHybridRollRadians(),
                config.maxHybridRollRadians());
    }

    private static float compressionActivation(
            DinosaurProceduralConfig config,
            List<DinosaurLegPose> legs,
            float terrainAngle,
            boolean lateralAxis) {
        if (Math.abs(terrainAngle) <= SAMPLE_EPSILON) {
            return 0.0F;
        }

        double minimumCoordinate = Double.POSITIVE_INFINITY;
        double maximumCoordinate = Double.NEGATIVE_INFINITY;
        for (DinosaurLegRig leg : config.legs()) {
            double coordinate = lateralAxis
                    ? leg.renderedModelXOffset()
                    : leg.modelZOffset();
            minimumCoordinate = Math.min(minimumCoordinate, coordinate);
            maximumCoordinate = Math.max(maximumCoordinate, coordinate);
        }
        if (maximumCoordinate - minimumCoordinate <= SAMPLE_EPSILON) {
            return 0.0F;
        }

        double center = (minimumCoordinate + maximumCoordinate) * 0.5;
        float shortestExtension = Float.POSITIVE_INFINITY;
        boolean lowSideUnreachable = false;
        for (DinosaurLegPose leg : legs) {
            DinosaurLegRig rig = config.leg(leg.legId());
            double coordinate = lateralAxis
                    ? rig.renderedModelXOffset()
                    : rig.modelZOffset();
            boolean onHighSide =
                    -terrainAngle * (coordinate - center) > SAMPLE_EPSILON;
            if (leg.planted() && onHighSide) {
                shortestExtension = Math.min(shortestExtension, leg.extensionFraction());
            }
            boolean onLowSide =
                    -terrainAngle * (coordinate - center) < -SAMPLE_EPSILON;
            if (onLowSide && !leg.reachable()) {
                lowSideUnreachable = true;
            }
        }
        if (lowSideUnreachable) {
            return 1.0F;
        }
        if (!Float.isFinite(shortestExtension)
                || shortestExtension >= config.bodyTiltStartExtensionFraction()) {
            return 0.0F;
        }

        float activation = Mth.clamp(
                (config.bodyTiltStartExtensionFraction() - shortestExtension)
                        / (config.bodyTiltStartExtensionFraction()
                                - config.minLegReachFraction()),
                0.0F,
                1.0F);
        return activation * activation * (3.0F - 2.0F * activation);
    }

    private record FootGroundFit(Vec3 position, FootTilt tilt) {
    }

    private record FootSurfaceSample(
            double localX,
            double localZ,
            double height) {
    }

    private record FootTilt(float pitchRadians, float rollRadians) {
        private static final FootTilt NONE = new FootTilt(0.0F, 0.0F);
    }

    private record RecoveryLean(
            float pitchRadians,
            float rollRadians) {
        private static final RecoveryLean NONE =
                new RecoveryLean(0.0F, 0.0F);
    }

    private record PlaneSlope(
            double lateralHeightPerBlock,
            double longitudinalHeightPerBlock,
            boolean lateralResolved,
            boolean longitudinalResolved) {
        private static final PlaneSlope UNRESOLVED =
                new PlaneSlope(0.0, 0.0, false, false);
    }

    private record SlopeEstimate(
            float pitchRadians,
            float rollRadians,
            boolean pitchResolved,
            boolean rollResolved) {
    }
}
