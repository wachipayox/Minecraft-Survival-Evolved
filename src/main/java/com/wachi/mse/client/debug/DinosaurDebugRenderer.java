package com.wachi.mse.client.debug;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSample;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DinosaurDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final float POINT_SIZE = 0.11F;
    private static final int[] LEG_COLORS = {
        0xFFFFD91A,
        0xFF1AE6FF,
        0xFFFF731A,
        0xFFBF4DFF,
        0xFF66FF8C,
        0xFFFF66B3
    };

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
            if (!(candidate instanceof LivingEntity dinosaur)
                    || !(candidate instanceof ProceduralDinosaur)
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
            LivingEntity dinosaur) {
        for (DinosaurTerrainSample sample : pose.samples()) {
            DinosaurLegPose leg = pose.leg(sample.legId());
            int color = sampleColor(sample);
            Vec3 point = sample.position();
            Gizmos.point(point, color, POINT_SIZE);
            Gizmos.line(
                    new Vec3(point.x, pose.origin().y, point.z),
                    point,
                    color,
                    2.0F);
            if (leg != null) {
                Vec3 target = new Vec3(
                        point.x,
                        pose.origin().y + leg.targetFootHeightOffset(),
                        point.z);
                Vec3 solved = new Vec3(
                        point.x,
                        pose.origin().y + leg.solvedFootHeightOffset(),
                        point.z);
                int ikColor = leg.reachable() ? 0xFF55FF55 : 0xFFFF55FF;
                Gizmos.point(solved, ikColor, POINT_SIZE * 0.75F);
                Gizmos.line(target, solved, ikColor, 1.5F);
            }
            Gizmos.billboardText(
                    String.format(
                            Locale.ROOT,
                            "%s %+.2f w%.2f k%+.0f\u00b0 e%.2f",
                            sample.shortName(),
                            sample.heightOffset(),
                            sample.supportWeight(),
                            leg == null
                                    ? 0.0
                                    : Math.toDegrees(leg.kneeRotation().xRadians()),
                            leg == null ? 0.0 : leg.extensionFraction()),
                    point.add(0.0, 0.14, 0.0),
                    TextGizmo.Style
                            .forColorAndCentered(sampleTextColor(sample))
                            .withScale(0.2F));
        }

        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "MSE body %+.1f\u00b0/%+.1f\u00b0 terrain %+.1f\u00b0/%+.1f\u00b0"
                                + " / rootY %+.2f / IK %d/%d / ground %d/%d",
                        Math.toDegrees(pose.pitchRadians()),
                        Math.toDegrees(pose.rollRadians()),
                        Math.toDegrees(pose.terrainPitchRadians()),
                        Math.toDegrees(pose.terrainRollRadians()),
                        pose.bodyTranslationYBlocks(),
                        pose.reachableLegCount(),
                        pose.legs().size(),
                        pose.validSampleCount(),
                        pose.samples().size()),
                pose.origin().add(0.0, dinosaur.getBbHeight() + 0.4, 0.0),
                TextGizmo.Style
                        .forColorAndCentered(poseColor(pose))
                        .withScale(0.25F));
        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "gait %.2f / activity %.2f",
                        pose.gait().phase(),
                        pose.gait().activity()),
                pose.origin().add(0.0, dinosaur.getBbHeight() + 0.18, 0.0),
                TextGizmo.Style
                        .forColorAndCentered(0xFF80D8FF)
                        .withScale(0.2F));
    }

    private static int poseColor(DinosaurProceduralPose pose) {
        if (pose.fullyResolved() && pose.validSampleCount() == pose.samples().size()) {
            return 0xFF55FF55;
        }
        return pose.terrainValid() ? 0xFFFFFF55 : 0xFFFFAA00;
    }

    private static int sampleColor(DinosaurTerrainSample sample) {
        if (sample.supportWeight() < 0.05F) {
            return 0xFF777777;
        }
        if (!sample.valid()) {
            return 0xFFFF3333;
        }

        return LEG_COLORS[Math.floorMod(sample.legId().hashCode(), LEG_COLORS.length)];
    }

    private static int sampleTextColor(DinosaurTerrainSample sample) {
        if (sample.supportWeight() < 0.05F) {
            return 0xFFAAAAAA;
        }
        return sample.valid() ? 0xFFFFFFFF : 0xFFFF5555;
    }
}
