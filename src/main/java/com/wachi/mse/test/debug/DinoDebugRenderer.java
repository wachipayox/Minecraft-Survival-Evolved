package com.wachi.mse.test.debug;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurStabilityAssessment;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSample;
import com.wachi.mse.test.dino.DinoEntity;
import com.wachi.mse.test.dino.DinoLeg;
import com.wachi.mse.test.dino.DinoLegPair;
import com.wachi.mse.test.terrain.TerrainWatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class DinoDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
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

    public DinoDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(
            double cameraX,
            double cameraY,
            double cameraZ,
            DebugValueAccess debugValues,
            Frustum frustum,
            float pTicks) {
        if (this.minecraft.level == null
          //      || !this.minecraft.getDebugOverlay().showDebugScreen()
        ) return;

        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);
        for (Entity candidate : this.minecraft.level.entitiesForRendering()) {
            if (!(candidate instanceof DinoEntity dinoEntity)
                    || dinoEntity.position().distanceToSqr(cameraPosition) > MAX_DISTANCE_SQUARED
                    || !frustum.isVisible(dinoEntity.getBoundingBox())) {
                continue;
            }


            for(DinoLegPair<DinoEntity> legPair : dinoEntity.hips) {

                Gizmos.line(
                        legPair.getCenter(pTicks),
                        legPair.getLeftLeg().getUpper(pTicks), 0xFF66FF8C
                );
                Gizmos.line(
                        legPair.getCenter(pTicks),
                        legPair.getRightLeg().getUpper(pTicks), 0xFF66FF8C
                );

                for (DinoLeg<DinoEntity> leg : legPair.getLegs()) {
                    DinoLeg.DLegGeometry geometry = leg.calculateGeometry(leg.actualPose, pTicks);

//                    for (Capsule collision : geometry.getCollisions(leg.thickness)) {
//                        Gizmos.cuboid(
//                                collision.bounds(), GizmoStyle.stroke(0xFF1AE6FF)
//                        );
//                    }
                    for (TerrainWatcher terrainWatcher : dinoEntity.getTerrainWatchers().values()) {
                        Gizmos.cuboid(terrainWatcher.requiredArea, GizmoStyle.stroke(0xFF1AE6FF));
                        Gizmos.cuboid(terrainWatcher.comfortArea, GizmoStyle.stroke(0xFFFF731A));
                        Gizmos.cuboid(terrainWatcher.cachedArea, GizmoStyle.stroke(0xFFBF4DFF));
                    }

                    Gizmos.line(geometry.upper(), geometry.knee(), 0xFF1AE6FF);
                    Gizmos.line(geometry.knee(), geometry.end(), 0xFFFF731A);
                    Gizmos.circle(
                            leg.getUpper(pTicks),
                            (float) leg.thickness, GizmoStyle.stroke(0xFF1AE6FF)
                    );
                    Gizmos.circle(
                            geometry.end(),
                            (float) leg.thickness, GizmoStyle.stroke(0xFFFF731A)
                    );
                }
            }
        }
    }




    private static void emitPoseGizmos(
            DinosaurProceduralPose dinoPose,
            LivingEntity dinosaur
    ) {
        float modelScale = ((ProceduralDinosaur) dinosaur)
                .proceduralConfig()
                .scale();
        float markerScale = (float) Math.sqrt(modelScale);

        for (DinosaurTerrainSample sample : dinoPose.samples()) {
            DinosaurLegPose leg = dinoPose.leg(sample.legId());
            int color = sampleColor(sample);
            Vec3 point = sample.position();
            Gizmos.point(point, color, POINT_SIZE * markerScale);
            Gizmos.line(
                    new Vec3(point.x, dinoPose.origin().y, point.z),
                    point,
                    color,
                    2.0F);
            if (leg != null) {
                Vec3 target = new Vec3(
                        point.x,
                        dinoPose.origin().y + leg.targetFootHeightOffset(),
                        point.z);
                Vec3 solved = new Vec3(
                        point.x,
                        dinoPose.origin().y + leg.solvedFootHeightOffset(),
                        point.z);
                int ikColor = leg.reachable() ? 0xFF55FF55 : 0xFFFF55FF;
                Gizmos.point(
                        solved,
                        ikColor,
                        POINT_SIZE * 0.75F * markerScale);
                Gizmos.line(target, solved, ikColor, 1.5F);
            }
            Gizmos.billboardText(
                    String.format(
                            Locale.ROOT,
                            "%s h%+.2f w%.2f k%+.0f\u00b0 e%.2f %s d%+.2f%s",
                            sample.shortName(),
                            sample.heightOffset(),
                            sample.supportWeight(),
                            leg == null
                                    ? 0.0
                                    : Math.toDegrees(leg.kneeRotation().xRadians()),
                            leg == null ? 0.0 : leg.extensionFraction(),
                            leg == null ? "--" : legStateLabel(leg),
                            leg == null
                                    ? 0.0
                                    : leg.solvedFootHeightOffset()
                                            - leg.targetFootHeightOffset(),
                            leg != null && leg.forcedMaximumExtension()
                                    ? " MAX"
                                    : ""),
                    point.add(0.0, 0.14 * markerScale, 0.0),
                    TextGizmo.Style
                            .forColorAndCentered(sampleTextColor(sample))
                            .withScale(0.2F * markerScale));
        }

        emitStabilityGizmos(dinoPose, modelScale, markerScale);
        emitOrientationGizmos(dinoPose, dinosaur, modelScale);
        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "MSE body %+.1f\u00b0/%+.1f\u00b0 terrain %+.1f\u00b0/%+.1f\u00b0"
                                + " / rootY %+.2f / IK %d/%d / ground %d/%d",
                        Math.toDegrees(dinoPose.pitchRadians()),
                        Math.toDegrees(dinoPose.rollRadians()),
                        Math.toDegrees(dinoPose.terrainPitchRadians()),
                        Math.toDegrees(dinoPose.terrainRollRadians()),
                        dinoPose.bodyTranslationYBlocks(),
                        dinoPose.reachableLegCount(),
                        dinoPose.legs().size(),
                        dinoPose.validSampleCount(),
                        dinoPose.samples().size()),
                dinoPose.origin().add(
                        0.0,
                        dinosaur.getBbHeight() + 0.4 * markerScale,
                        0.0),
                TextGizmo.Style
                        .forColorAndCentered(poseColor(dinoPose))
                        .withScale(0.25F * markerScale));
        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "neck %+.1f\u00b0/%+.1f\u00b0 / gait %.2f / activity %.2f",
                        Math.toDegrees(dinoPose.orientation().yawRadians()),
                        Math.toDegrees(dinoPose.orientation().pitchRadians()),
                        dinoPose.gait().phase(),
                        dinoPose.gait().activity()),
                dinoPose.origin().add(
                        0.0,
                        dinosaur.getBbHeight() + 0.18 * markerScale,
                        0.0),
                TextGizmo.Style
                        .forColorAndCentered(0xFF80D8FF)
                        .withScale(0.2F * markerScale));
        DinosaurStabilityAssessment stability = dinoPose.stability();
        Gizmos.billboardText(
                String.format(
                        Locale.ROOT,
                        "balance %s / margin %s / support %d/%d / aabb %.2f",
                        stabilityLabel(stability),
                        stability.evaluable()
                                        && Double.isFinite(
                                                stability.signedMarginBlocks())
                                ? String.format(
                                        Locale.ROOT,
                                        "%+.2f",
                                        stability.signedMarginBlocks())
                                : stability.supportingLegCount() == 0
                                        ? "void"
                                        : "--",
                        stability.supportingLegCount(),
                        dinoPose.legs().size(),
                        stability
                                .footprintSupport()
                                .areaBlocksSquared()),
                dinoPose.origin().add(
                        0.0,
                        dinosaur.getBbHeight() - 0.04 * markerScale,
                        0.0),
                TextGizmo.Style
                        .forColorAndCentered(stabilityColor(stability))
                        .withScale(0.2F * markerScale));
    }

    private static void emitOrientationGizmos(
            DinosaurProceduralPose pose,
            LivingEntity dinosaur,
            float modelScale) {
        Vec3 origin = pose.origin().add(0.0, dinosaur.getBbHeight() * 0.72, 0.0);
        Vec3 bodyDirection = Vec3.directionFromRotation(0.0F, pose.bodyYawDegrees());
        float lookYaw = pose.bodyYawDegrees()
                + (float) Math.toDegrees(pose.orientation().yawRadians());
        float lookPitch = (float) Math.toDegrees(pose.orientation().pitchRadians());
        // directionFromRotation expects vanilla pitch (negative means up);
        // the procedural pose stores GeckoLib visual pitch (positive up).
        Vec3 lookDirection = Vec3.directionFromRotation(-lookPitch, lookYaw);
        Gizmos.arrow(
                origin,
                origin.add(bodyDirection.scale(1.1 * modelScale)),
                0xFF3388FF,
                2.0F);
        Gizmos.arrow(
                origin.add(0.0, 0.05, 0.0),
                origin.add(lookDirection.scale(1.25 * modelScale))
                        .add(0.0, 0.05, 0.0),
                0xFFFF55CC,
                2.5F);
    }

    private static void emitStabilityGizmos(
            DinosaurProceduralPose pose,
            float modelScale,
            float markerScale) {
        DinosaurStabilityAssessment stability = pose.stability();
        List<Vec3> hull = stability.supportHull();
        for (int index = 0; index < hull.size(); index++) {
            Vec3 start = hull.get(index).add(0.0, 0.025, 0.0);
            Vec3 end = hull.get((index + 1) % hull.size()).add(0.0, 0.025, 0.0);
            Gizmos.line(start, end, stabilityColor(stability), 2.0F);
        }

        Vec3 center = stability.centerOfMassWorld().add(0.0, 0.08, 0.0);
        Gizmos.point(
                center,
                stabilityColor(stability),
                POINT_SIZE * markerScale);
        if (stability.footprintSupport().present()) {
            Vec3 footprintCenter = stability
                    .footprintSupport()
                    .centerWorld()
                    .add(0.0, 0.04, 0.0);
            Gizmos.point(
                    footprintCenter,
                    0xFF00FFFF,
                    POINT_SIZE * markerScale);
            Gizmos.line(footprintCenter, center, 0xFF00FFFF, 1.5F);
        }
        if (stability.requiresRecovery()) {
            Gizmos.line(
                    center,
                    center.add(
                            stability.fallDirectionWorld()
                                    .scale(0.75 * modelScale)),
                    0xFFFF3333,
                    3.0F);
        }
    }

    private static int poseColor(DinosaurProceduralPose pose) {
        if (pose.stability().requiresRecovery()) {
            return 0xFFFF5555;
        }
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

    private static String legStateLabel(DinosaurLegPose leg) {
        if (!leg.terrainContact()) {
            return "VOID";
        }
        return switch (leg.reachStatus()) {
            case REACHABLE -> "OK";
            case COMPRESSED -> "FLEX";
            case TOO_CLOSE -> "NEAR";
            case TOO_FAR -> "FAR";
            case TARGET_NOT_BELOW_HIP -> "HIGH";
        };
    }

    private static String stabilityLabel(DinosaurStabilityAssessment stability) {
        if (stability.supportingLegCount() == 0) {
            return "unsupported";
        }
        if (!stability.evaluable()) {
            return "unresolved";
        }
        return stability.stable() ? "stable" : "unstable";
    }

    private static int stabilityColor(DinosaurStabilityAssessment stability) {
        if (!stability.evaluable()) {
            return 0xFFFFAA00;
        }
        return stability.stable() ? 0xFF55FF55 : 0xFFFF5555;
    }
}
