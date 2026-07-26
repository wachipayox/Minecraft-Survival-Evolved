package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes a terrain pose without consulting goals, navigation, NoAI or the
 * entity's cached on-ground flag.
 */
public final class DinosaurTerrainSampler {
    private static final double SAMPLE_EPSILON = 1.0E-5;

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
        return sampleAt(
                entity,
                config,
                entity.position(),
                entity.getYRot(),
                DinosaurGaitState.sampleAuthoritative(entity, config),
                orientationPose(
                        config,
                        entity.getYRot(),
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
        CollisionContext collisionContext = CollisionContext.of(entity);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        List<DinosaurTerrainSample> samples = new ArrayList<>(config.legs().size());

        for (DinosaurLegRig leg : config.legs()) {
            double observationBelow = observationDepthBelow(config, leg);
            GroundHit hit = findBestGroundHit(
                    level,
                    collisionContext,
                    mutableBlockPos,
                    origin,
                    leg,
                    config.contactPatchRadius(),
                    sinYaw,
                    cosYaw,
                    config.sampleAbove(),
                    observationBelow);
            boolean valid = hit != null;
            Vec3 position;
            if (valid) {
                position = hit.position();
            } else {
                // Keep the old bounded visual drop when no floor exists at
                // all. The farther awareness range is only allowed to affect
                // the pose when it finds real collision geometry.
                Vec3 nominalPosition = modelPointToWorld(
                        origin,
                        leg.modelXOffset(),
                        leg.modelZOffset(),
                        sinYaw,
                        cosYaw);
                position = new Vec3(
                        nominalPosition.x,
                        origin.y - config.sampleBelow(),
                        nominalPosition.z);
            }
            DinosaurTerrainSample sample = new DinosaurTerrainSample(
                    leg.id(),
                    leg.shortName(),
                    position,
                    position.y - origin.y,
                    valid,
                    gait.supportWeight(leg.id()));
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
                gait,
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
                gait,
                bodyTranslationY,
                bodyPitch,
                bodyRoll);
        DinosaurStabilityAssessment stability = DinosaurStabilitySolver.assess(
                config,
                origin,
                bodyYawDegrees,
                samples,
                legs);

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
                terrainPitch,
                terrainRoll,
                slope.pitchResolved(),
                slope.rollResolved(),
                gait,
                samples,
                legs,
                stability);
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
        float pitchDegrees = Mth.clamp(
                headPitchDegrees,
                -config.orientation().maxPitchUpDegrees(),
                config.orientation().maxPitchDownDegrees());
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

    private static GroundHit findBestGroundHit(
            Level level,
            CollisionContext collisionContext,
            BlockPos.MutableBlockPos mutableBlockPos,
            Vec3 origin,
            DinosaurLegRig leg,
            double contactPatchRadius,
            double sinYaw,
            double cosYaw,
            double sampleAbove,
            double sampleBelow) {
        Vec3 nominalPoint = modelPointToWorld(
                origin,
                leg.modelXOffset(),
                leg.modelZOffset(),
                sinYaw,
                cosYaw);
        int minBlockX = Mth.floor(nominalPoint.x - contactPatchRadius);
        int maxBlockX = Mth.floor(nominalPoint.x + contactPatchRadius);
        int minBlockZ = Mth.floor(nominalPoint.z - contactPatchRadius);
        int maxBlockZ = Mth.floor(nominalPoint.z + contactPatchRadius);
        int startY = Mth.floor(origin.y + sampleAbove);
        int endY = Mth.floor(origin.y - sampleBelow);
        double radiusSquared = contactPatchRadius * contactPatchRadius;
        GroundHit bestHit = null;

        for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
            for (int blockZ = minBlockZ; blockZ <= maxBlockZ; blockZ++) {
                if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                    continue;
                }

                for (int blockY = startY; blockY >= endY; blockY--) {
                    mutableBlockPos.set(blockX, blockY, blockZ);
                    VoxelShape shape = level
                            .getBlockState(mutableBlockPos)
                            .getCollisionShape(level, mutableBlockPos, collisionContext);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (AABB box : shape.toAabbs()) {
                        double contactX = Mth.clamp(
                                nominalPoint.x,
                                blockX + box.minX,
                                blockX + box.maxX);
                        double contactZ = Mth.clamp(
                                nominalPoint.z,
                                blockZ + box.minZ,
                                blockZ + box.maxZ);
                        double distanceSquared =
                                square(contactX - nominalPoint.x)
                                        + square(contactZ - nominalPoint.z);
                        if (distanceSquared > radiusSquared + SAMPLE_EPSILON) {
                            continue;
                        }

                        double worldTop = blockY + box.maxY;
                        if (worldTop > origin.y + sampleAbove + SAMPLE_EPSILON
                                || worldTop < origin.y - sampleBelow - SAMPLE_EPSILON) {
                            continue;
                        }

                        if (bestHit == null
                                || worldTop > bestHit.position().y + SAMPLE_EPSILON
                                || (Math.abs(worldTop - bestHit.position().y) <= SAMPLE_EPSILON
                                        && distanceSquared < bestHit.distanceSquared())) {
                            bestHit = new GroundHit(
                                    new Vec3(contactX, worldTop, contactZ),
                                    distanceSquared);
                        }
                    }
                }
            }
        }
        return bestHit;
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

    private static double square(double value) {
        return value * value;
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
     * Weighted least-squares fit of {@code y = ax + bz + c}. A biped whose
     * feet share Z can still resolve roll; a layout with no longitudinal or
     * lateral spread simply leaves that axis unresolved. Collinear diagonal
     * supports deliberately resolve only their strongest axis because one
     * line cannot uniquely determine a two-dimensional terrain plane.
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
            if (weight <= SAMPLE_EPSILON) {
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
            if (weight <= SAMPLE_EPSILON) {
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
        return Mth.clamp(
                terrainPitch * config.bodyTiltSlopeShare() * activation,
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
        // GeckoLib negates model X, so a high anatomical left side needs
        // negative Z rotation.
        return Mth.clamp(
                -terrainRoll * config.bodyTiltSlopeShare() * activation,
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

    private record GroundHit(Vec3 position, double distanceSquared) {
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
