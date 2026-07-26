package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportPoint;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportProbe;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config,
            float partialTick) {
        Vec3 origin = new Vec3(
                Mth.lerp((double) partialTick, entity.xo, entity.getX()),
                Mth.lerp((double) partialTick, entity.yo, entity.getY()),
                Mth.lerp((double) partialTick, entity.zo, entity.getZ()));
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        return sampleAt(entity, config, origin, bodyYaw);
    }

    /**
     * Entry point for logical/server code that needs the terrain-derived body
     * orientation at the entity's authoritative tick position.
     */
    public static DinosaurProceduralPose sampleAuthoritative(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config) {
        return sampleAt(entity, config, entity.position(), entity.yBodyRot);
    }

    /**
     * Deterministic core shared by client and server. It does not mutate the
     * entity and does not force chunk loads.
     */
    public static DinosaurProceduralPose sampleAt(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees) {
        Level level = entity.level();
        double modelYawRadians = Math.toRadians(180.0F - bodyYawDegrees);
        double sinYaw = Math.sin(modelYawRadians);
        double cosYaw = Math.cos(modelYawRadians);
        CollisionContext collisionContext = CollisionContext.of(entity);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        List<DinosaurTerrainSample> samples = new ArrayList<>(4);
        Map<SupportPoint, DinosaurTerrainSample> byPoint =
                new EnumMap<>(SupportPoint.class);

        for (SupportProbe probe : config.supportProbes()) {
            GroundHit hit = findBestGroundHit(
                    level,
                    collisionContext,
                    mutableBlockPos,
                    origin,
                    probe,
                    config.contactPatchRadius(),
                    sinYaw,
                    cosYaw,
                    config.sampleAbove(),
                    config.sampleBelow());
            boolean valid = hit != null;
            Vec3 position = valid
                    ? hit.position()
                    : modelPointToWorld(
                            origin,
                            probe.modelXOffset(),
                            probe.modelZOffset(),
                            sinYaw,
                            cosYaw);
            DinosaurTerrainSample sample = new DinosaurTerrainSample(
                    probe.point(),
                    position,
                    valid ? position.y - origin.y : 0.0,
                    valid);
            samples.add(sample);
            byPoint.put(probe.point(), sample);
        }

        double frontHeight = averageValidHeight(
                byPoint.get(SupportPoint.FRONT_LEFT),
                byPoint.get(SupportPoint.FRONT_RIGHT));
        double backHeight = averageValidHeight(
                byPoint.get(SupportPoint.BACK_LEFT),
                byPoint.get(SupportPoint.BACK_RIGHT));
        double leftHeight = averageValidHeight(
                byPoint.get(SupportPoint.FRONT_LEFT),
                byPoint.get(SupportPoint.BACK_LEFT));
        double rightHeight = averageValidHeight(
                byPoint.get(SupportPoint.FRONT_RIGHT),
                byPoint.get(SupportPoint.BACK_RIGHT));
        boolean pitchResolved =
                Double.isFinite(frontHeight) && Double.isFinite(backHeight);
        boolean rollResolved =
                Double.isFinite(leftHeight) && Double.isFinite(rightHeight);
        double longitudinalDistance =
                Math.abs(config.backLeft().modelZOffset() - config.frontLeft().modelZOffset());
        double lateralDistance =
                Math.abs(config.frontLeft().modelXOffset() - config.frontRight().modelXOffset());
        float pitch = pitchResolved
                ? limitedSlope(
                        frontHeight - backHeight,
                        longitudinalDistance,
                        config.maxPitchRadians(),
                        config.slopeDeadzoneRadians())
                : 0.0F;
        float roll = rollResolved
                ? limitedSlope(
                        leftHeight - rightHeight,
                        lateralDistance,
                        config.maxRollRadians(),
                        config.slopeDeadzoneRadians())
                : 0.0F;

        return new DinosaurProceduralPose(
                origin,
                bodyYawDegrees,
                pitch,
                roll,
                pitch,
                roll,
                pitchResolved,
                rollResolved,
                samples);
    }

    private static GroundHit findBestGroundHit(
            Level level,
            CollisionContext collisionContext,
            BlockPos.MutableBlockPos mutableBlockPos,
            Vec3 origin,
            SupportProbe probe,
            double contactPatchRadius,
            double sinYaw,
            double cosYaw,
            double sampleAbove,
            double sampleBelow) {
        Vec3 nominalPoint = modelPointToWorld(
                origin,
                probe.modelXOffset(),
                probe.modelZOffset(),
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
        return new Vec3(
                origin.x + cosYaw * modelX + sinYaw * modelZ,
                origin.y,
                origin.z - sinYaw * modelX + cosYaw * modelZ);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double averageValidHeight(
            DinosaurTerrainSample first,
            DinosaurTerrainSample second) {
        double total = 0.0;
        int count = 0;
        if (first != null && first.valid()) {
            total += first.position().y;
            count++;
        }
        if (second != null && second.valid()) {
            total += second.position().y;
            count++;
        }
        return count == 0 ? Double.NaN : total / count;
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

    private record GroundHit(Vec3 position, double distanceSquared) {
    }
}
