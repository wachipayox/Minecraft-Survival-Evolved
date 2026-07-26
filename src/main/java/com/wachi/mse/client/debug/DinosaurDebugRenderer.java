package com.wachi.mse.client.debug;

import com.wachi.mse.client.animation.DinosaurProceduralPose;
import com.wachi.mse.client.animation.DinosaurTerrainSample;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class DinosaurDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final float POINT_SIZE = 0.11F;

    private final Minecraft minecraft;

    public DinosaurDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(
            double cameraX,
            double cameraY,
            double cameraZ,
            DebugValueAccess debugValues,
            Frustum frustum,
            float partialTicks) {
        if (this.minecraft.level == null
                || !this.minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);
        for (Entity candidate : this.minecraft.level.entitiesForRendering()) {
            if (!(candidate instanceof PrototypeDinosaurEntity dinosaur)
                    || dinosaur.position().distanceToSqr(cameraPosition) > MAX_DISTANCE_SQUARED
                    || !frustum.isVisible(dinosaur.getBoundingBox())) {
                continue;
            }

            DinosaurProceduralPose pose = DinosaurDebugPoseStore.get(dinosaur);
            if (pose == null) {
                continue;
            }

            emitPoseGizmos(pose, dinosaur);
        }
    }

    private static void emitPoseGizmos(
            DinosaurProceduralPose pose,
            PrototypeDinosaurEntity dinosaur) {
        for (DinosaurTerrainSample sample : pose.samples()) {
            int color = sampleColor(sample);
            Vec3 point = sample.position();
            Gizmos.point(point, color, POINT_SIZE);
            Gizmos.line(
                    new Vec3(point.x, pose.origin().y, point.z),
                    point,
                    color,
                    2.0F);
            Gizmos.billboardText(
                    String.format(
                            Locale.ROOT,
                            "%s %+.2f",
                            sample.point().shortName(),
                            sample.heightOffset()),
                    point.add(0.0, 0.14, 0.0),
                    TextGizmo.Style
                            .forColorAndCentered(sample.valid() ? 0xFFFFFFFF : 0xFFFF5555)
                            .withScale(0.2F));
        }

        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "MSE pitch %+.1f\u00b0 / roll %+.1f\u00b0",
                        Math.toDegrees(pose.pitchRadians()),
                        Math.toDegrees(pose.rollRadians())),
                pose.origin().add(0.0, dinosaur.getBbHeight() + 0.4, 0.0),
                TextGizmo.Style
                        .forColorAndCentered(pose.terrainValid() ? 0xFF55FF55 : 0xFFFFAA00)
                        .withScale(0.25F));
    }

    private static int sampleColor(DinosaurTerrainSample sample) {
        if (!sample.valid()) {
            return 0xFFFF3333;
        }

        return switch (sample.point()) {
            case FRONT_LEFT -> 0xFFFFD91A;
            case FRONT_RIGHT -> 0xFF1AE6FF;
            case BACK_LEFT -> 0xFFFF731A;
            case BACK_RIGHT -> 0xFFBF4DFF;
        };
    }
}
