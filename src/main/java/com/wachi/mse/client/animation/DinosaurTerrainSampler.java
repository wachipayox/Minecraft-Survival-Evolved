package com.wachi.mse.client.animation;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportPoint;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportProbe;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DinosaurTerrainSampler {
    private static final double SAMPLE_EPSILON = 1.0E-5;

    private final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

    public DinosaurProceduralPose sample(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config,
            float partialTick) {
        ClientLevel level = (ClientLevel) entity.level();
        double baseX = Mth.lerp((double) partialTick, entity.xo, entity.getX());
        double baseY = Mth.lerp((double) partialTick, entity.yo, entity.getY());
        double baseZ = Mth.lerp((double) partialTick, entity.zo, entity.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        double modelYawRadians = Math.toRadians(180.0F - bodyYaw);
        double sinYaw = Math.sin(modelYawRadians);
        double cosYaw = Math.cos(modelYawRadians);
        CollisionContext collisionContext = CollisionContext.of(entity);
        List<DinosaurTerrainSample> samples = new ArrayList<>(4);
        Map<SupportPoint, DinosaurTerrainSample> byPoint = new EnumMap<>(SupportPoint.class);

        for (SupportProbe probe : config.supportProbes()) {
            double worldX = baseX + cosYaw * probe.modelXOffset() + sinYaw * probe.modelZOffset();
            double worldZ = baseZ - sinYaw * probe.modelXOffset() + cosYaw * probe.modelZOffset();
            double groundY = findGroundHeight(
                    level,
                    collisionContext,
                    worldX,
                    baseY,
                    worldZ,
                    config.sampleAbove(),
                    config.sampleBelow());
            boolean valid = Double.isFinite(groundY);
            DinosaurTerrainSample sample = new DinosaurTerrainSample(
                    probe.point(),
                    new Vec3(worldX, valid ? groundY : baseY, worldZ),
                    valid ? groundY - baseY : 0.0,
                    valid);
            samples.add(sample);
            byPoint.put(probe.point(), sample);
        }

        boolean terrainValid = entity.onGround() && samples.stream().allMatch(DinosaurTerrainSample::valid);
        float pitch = 0.0F;
        float roll = 0.0F;
        if (terrainValid) {
            double frontHeight = averageHeight(
                    byPoint.get(SupportPoint.FRONT_LEFT),
                    byPoint.get(SupportPoint.FRONT_RIGHT));
            double backHeight = averageHeight(
                    byPoint.get(SupportPoint.BACK_LEFT),
                    byPoint.get(SupportPoint.BACK_RIGHT));
            double leftHeight = averageHeight(
                    byPoint.get(SupportPoint.FRONT_LEFT),
                    byPoint.get(SupportPoint.BACK_LEFT));
            double rightHeight = averageHeight(
                    byPoint.get(SupportPoint.FRONT_RIGHT),
                    byPoint.get(SupportPoint.BACK_RIGHT));
            double longitudinalDistance =
                    Math.abs(config.backLeft().modelZOffset() - config.frontLeft().modelZOffset());
            double lateralDistance =
                    Math.abs(config.frontLeft().modelXOffset() - config.frontRight().modelXOffset());

            pitch = limitedSlope(
                    frontHeight - backHeight,
                    longitudinalDistance,
                    config.maxPitchRadians(),
                    config.slopeDeadzoneRadians());
            roll = limitedSlope(
                    leftHeight - rightHeight,
                    lateralDistance,
                    config.maxRollRadians(),
                    config.slopeDeadzoneRadians());
        }

        return new DinosaurProceduralPose(
                new Vec3(baseX, baseY, baseZ),
                pitch,
                roll,
                pitch,
                roll,
                terrainValid,
                samples);
    }

    private double findGroundHeight(
            ClientLevel level,
            CollisionContext collisionContext,
            double worldX,
            double baseY,
            double worldZ,
            double sampleAbove,
            double sampleBelow) {
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        int startY = Mth.floor(baseY + sampleAbove);
        int endY = Mth.floor(baseY - sampleBelow);
        double localX = Mth.clamp(worldX - blockX, SAMPLE_EPSILON, 1.0 - SAMPLE_EPSILON);
        double localZ = Mth.clamp(worldZ - blockZ, SAMPLE_EPSILON, 1.0 - SAMPLE_EPSILON);

        for (int blockY = startY; blockY >= endY; blockY--) {
            this.mutableBlockPos.set(blockX, blockY, blockZ);
            if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                return Double.NaN;
            }

            VoxelShape shape = level
                    .getBlockState(this.mutableBlockPos)
                    .getCollisionShape(level, this.mutableBlockPos, collisionContext);
            if (shape.isEmpty()) {
                continue;
            }

            // For a Y-axis lookup VoxelShape expects the remaining axes in Z, X order.
            double localTop = shape.max(Direction.Axis.Y, localZ, localX);
            if (!Double.isFinite(localTop)) {
                continue;
            }

            double worldTop = blockY + localTop;
            if (worldTop <= baseY + sampleAbove + SAMPLE_EPSILON
                    && worldTop >= baseY - sampleBelow - SAMPLE_EPSILON) {
                return worldTop;
            }
        }

        return Double.NaN;
    }

    private static double averageHeight(DinosaurTerrainSample first, DinosaurTerrainSample second) {
        return (first.position().y + second.position().y) * 0.5;
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
}
