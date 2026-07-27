package com.wachi.mse.client.animation;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLookBone;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegIkSolver;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurOrientationPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

public final class DinosaurProceduralAnimator {
    private static final double MAX_SMOOTHING_GAP_TICKS = 5.0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;

    private final Map<LivingEntity, SmoothedPose> smoothedPoses = new WeakHashMap<>();

    public DinosaurProceduralPose smooth(
            LivingEntity entity,
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            float partialTick) {
        double renderTime = entity.level().getGameTime() + partialTick;
        SmoothedPose previous = this.smoothedPoses.get(entity);
        if (previous == null
                || renderTime < previous.renderTime()
                || renderTime - previous.renderTime() > MAX_SMOOTHING_GAP_TICKS) {
            float bodyTranslationY =
                    DinosaurLegIkSolver.constrainBodyTranslationY(
                            config,
                            target.samples(),
                            target.legs(),
                            target.targetPitchRadians(),
                            target.targetRollRadians(),
                            target.targetBodyTranslationYBlocks());
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    target.targetPitchRadians(),
                    target.targetRollRadians());
            SmoothedPose initial = new SmoothedPose(
                    target.targetPitchRadians(),
                    target.targetRollRadians(),
                    bodyTranslationY,
                    target.orientation(),
                    renderTime);
            this.smoothedPoses.put(entity, initial);
            return target.withSmoothedValues(
                    initial.pitch(),
                    initial.roll(),
                    initial.bodyTranslationY(),
                    initial.orientation(),
                    legs);
        }
        if (renderTime == previous.renderTime()) {
            float bodyTranslationY =
                    DinosaurLegIkSolver.constrainBodyTranslationY(
                            config,
                            target.samples(),
                            target.legs(),
                            previous.pitch(),
                            previous.roll(),
                            previous.bodyTranslationY());
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    previous.pitch(),
                    previous.roll());
            this.smoothedPoses.put(
                    entity,
                    new SmoothedPose(
                            previous.pitch(),
                            previous.roll(),
                            bodyTranslationY,
                            previous.orientation(),
                            renderTime));
            return target.withSmoothedValues(
                    previous.pitch(),
                    previous.roll(),
                    bodyTranslationY,
                    previous.orientation(),
                    legs);
        }

        double elapsedSeconds = (renderTime - previous.renderTime()) / 20.0;
        float alpha = (float) (1.0 - Math.exp(-config.smoothingResponsePerSecond() * elapsedSeconds));
        float pitch = lerp(previous.pitch(), target.targetPitchRadians(), alpha);
        float roll = lerp(previous.roll(), target.targetRollRadians(), alpha);
        float desiredBodyTranslationY = lerp(
                previous.bodyTranslationY(),
                target.targetBodyTranslationYBlocks(),
                alpha);
        float bodyTranslationY =
                DinosaurLegIkSolver.constrainBodyTranslationY(
                        config,
                        target.samples(),
                        target.legs(),
                        pitch,
                        roll,
                        desiredBodyTranslationY);
        float orientationAlpha = (float) (1.0 - Math.exp(
                -config.orientation().visualSmoothingResponsePerSecond()
                        * elapsedSeconds));
        DinosaurOrientationPose orientation = target.orientation().withSmoothedValues(
                previous.orientation().yawRadians()
                        + angularDelta(
                                previous.orientation().yawRadians(),
                                target.orientation().targetYawRadians())
                                * orientationAlpha,
                lerp(
                        previous.orientation().pitchRadians(),
                        target.orientation().targetPitchRadians(),
                        orientationAlpha));
        List<DinosaurLegPose> legs = solveVisualLegs(
                target,
                config,
                bodyTranslationY,
                pitch,
                roll);
        this.smoothedPoses.put(
                entity,
                new SmoothedPose(
                        pitch,
                        roll,
                        bodyTranslationY,
                        orientation,
                        renderTime));
        return target.withSmoothedValues(
                pitch,
                roll,
                bodyTranslationY,
                orientation,
                legs);
    }

    public void apply(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        float animationBodyY = snapshots
                .get(config.bones().body())
                .map(snapshot -> applyBodyPose(snapshot, pose, config))
                .orElse(0.0F);
        for (DinosaurLegPose leg : pose.legs()) {
            applyLegPose(
                    config.leg(leg.legId()),
                    compensateBodyAnimation(config, leg, pose, animationBodyY),
                    snapshots);
        }
        applyLookPose(pose, config, snapshots);
    }

    private static void applyLookPose(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        float pitchRelativeToTiltedBody =
                pose.orientation().pitchRadians() - pose.pitchRadians();
        for (DinosaurLookBone bone : config.orientation().lookBones()) {
            snapshots.get(bone.boneName()).ifPresent(snapshot -> {
                snapshot.setRotY(
                        snapshot.getRotY()
                                - pose.orientation().yawRadians() * bone.yawWeight());
                snapshot.setRotX(
                        snapshot.getRotX()
                                + pitchRelativeToTiltedBody * bone.pitchWeight());
            });
        }
    }

    private static float applyBodyPose(
            BoneSnapshot body,
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config) {
        float animationBodyY =
                body.getTranslateY()
                        / MODEL_UNITS_PER_BLOCK
                        * config.modelScale();
        body.setTranslateY(
                body.getTranslateY()
                        + pose.bodyTranslationYBlocks()
                                * MODEL_UNITS_PER_BLOCK
                                / config.modelScale());
        body.setRotX(body.getRotX() + pose.pitchRadians());
        body.setRotZ(body.getRotZ() + pose.rollRadians());
        return animationBodyY;
    }

    private static void applyLegPose(
            DinosaurLegRig rig,
            DinosaurLegPose pose,
            BoneSnapshots snapshots) {
        snapshots.get(rig.upperBone()).ifPresent(snapshot ->
                applyRotation(snapshot, pose.hipRotation()));
        snapshots.get(rig.lowerBone()).ifPresent(snapshot ->
                applyRotation(snapshot, pose.kneeRotation()));
        snapshots.get(rig.footBone()).ifPresent(snapshot ->
                applyFootRotation(snapshot, pose));
    }

    private static void applyRotation(
            BoneSnapshot snapshot,
            DinosaurBoneRotation rotation) {
        snapshot.setRotX(snapshot.getRotX() + rotation.xRadians());
        snapshot.setRotY(snapshot.getRotY() + rotation.yRadians());
        snapshot.setRotZ(snapshot.getRotZ() + rotation.zRadians());
    }

    private static void applyFootRotation(
            BoneSnapshot snapshot,
            DinosaurLegPose pose) {
        applyRotation(snapshot, pose.footRotation());
        snapshot.setRotX(
                snapshot.getRotX() + pose.footTerrainPitchRadians());
        snapshot.setRotZ(
                snapshot.getRotZ() + pose.footTerrainRollRadians());
    }

    private static DinosaurLegPose compensateBodyAnimation(
            DinosaurProceduralConfig config,
            DinosaurLegPose smoothedLeg,
            DinosaurProceduralPose pose,
            float animationBodyY) {
        if (Math.abs(animationBodyY) < 1.0E-5F) {
            return smoothedLeg;
        }

        DinosaurLegRig rig = config.leg(smoothedLeg.legId());
        return solveForBodyHeight(
                config,
                rig,
                smoothedLeg,
                pose.bodyTranslationYBlocks() + animationBodyY,
                pose.pitchRadians(),
                pose.rollRadians())
                .withFootTerrainTilt(
                        smoothedLeg.footTerrainPitchRadians(),
                        smoothedLeg.footTerrainRollRadians());
    }

    private static DinosaurLegPose solveForBodyHeight(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurLegPose reference,
            float bodyTranslationY,
            float bodyPitch,
            float bodyRoll) {
        if (reference.forcedMaximumExtension()) {
            return DinosaurLegIkSolver.solveMaximumExtension(
                    config,
                    rig,
                    bodyTranslationY,
                    bodyPitch,
                    bodyRoll,
                    reference.terrainContact())
                    .withRequestedState(reference);
        }
        return DinosaurLegIkSolver.solveTarget(
                config,
                rig,
                reference.targetFootHeightOffset(),
                bodyTranslationY,
                bodyPitch,
                bodyRoll,
                reference.planted(),
                reference.terrainContact());
    }

    /**
     * Solves every leg against the exact body transform that will be rendered.
     * Interpolating nonlinear IK bone angles independently from the body can
     * never preserve a planted contact, so body smoothing happens first and
     * this pass reconstructs all limb rotations coherently afterwards.
     */
    private static List<DinosaurLegPose> solveVisualLegs(
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            float bodyTranslationY,
            float bodyPitch,
            float bodyRoll) {
        Map<String, DinosaurTerrainSample> samplesByLegId = new HashMap<>();
        for (DinosaurTerrainSample sample : target.samples()) {
            samplesByLegId.put(sample.legId(), sample);
        }

        List<DinosaurLegPose> result =
                new ArrayList<>(target.legs().size());
        for (DinosaurLegPose targetLeg : target.legs()) {
            DinosaurTerrainSample sample =
                    samplesByLegId.get(targetLeg.legId());
            boolean planted = sample != null
                    ? sample.plantedCandidate()
                    : targetLeg.planted();
            if (targetLeg.forcedMaximumExtension()) {
                DinosaurLegPose extended =
                        DinosaurLegIkSolver.solveMaximumExtension(
                                config,
                                config.leg(targetLeg.legId()),
                                bodyTranslationY,
                                bodyPitch,
                                bodyRoll,
                                targetLeg.terrainContact());
                result.add(extended.withRequestedState(targetLeg));
                continue;
            }
            result.add(DinosaurLegIkSolver.solveTarget(
                    config,
                    config.leg(targetLeg.legId()),
                    targetLeg.targetFootHeightOffset(),
                    bodyTranslationY,
                    bodyPitch,
                    bodyRoll,
                    planted,
                    targetLeg.terrainContact())
                    .withFootTerrainTilt(
                            targetLeg.footTerrainPitchRadians(),
                            targetLeg.footTerrainRollRadians()));
        }
        return List.copyOf(result);
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    private static float angularDelta(float start, float end) {
        float delta = end - start;
        while (delta <= -Math.PI) {
            delta += (float) (Math.PI * 2.0);
        }
        while (delta > Math.PI) {
            delta -= (float) (Math.PI * 2.0);
        }
        return delta;
    }

    private record SmoothedPose(
            float pitch,
            float roll,
            float bodyTranslationY,
            DinosaurOrientationPose orientation,
            double renderTime) {
    }
}
