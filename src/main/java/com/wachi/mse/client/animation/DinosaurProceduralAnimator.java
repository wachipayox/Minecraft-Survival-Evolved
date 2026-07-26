package com.wachi.mse.client.animation;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegIkSolver;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.ArrayList;
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
            SmoothedPose initial = new SmoothedPose(
                    target.targetPitchRadians(),
                    target.targetRollRadians(),
                    target.targetBodyTranslationYBlocks(),
                    target.legs(),
                    renderTime);
            this.smoothedPoses.put(entity, initial);
            return target.withSmoothedValues(
                    initial.pitch(),
                    initial.roll(),
                    initial.bodyTranslationY(),
                    initial.legs());
        }
        if (renderTime == previous.renderTime()) {
            return target.withSmoothedValues(
                    previous.pitch(),
                    previous.roll(),
                    previous.bodyTranslationY(),
                    previous.legs());
        }

        double elapsedSeconds = (renderTime - previous.renderTime()) / 20.0;
        float alpha = (float) (1.0 - Math.exp(-config.smoothingResponsePerSecond() * elapsedSeconds));
        float pitch = lerp(previous.pitch(), target.targetPitchRadians(), alpha);
        float roll = lerp(previous.roll(), target.targetRollRadians(), alpha);
        float bodyTranslationY = lerp(
                previous.bodyTranslationY(),
                target.targetBodyTranslationYBlocks(),
                alpha);
        List<DinosaurLegPose> legs = smoothLegs(previous.legs(), target.legs(), alpha);
        this.smoothedPoses.put(
                entity,
                new SmoothedPose(pitch, roll, bodyTranslationY, legs, renderTime));
        return target.withSmoothedValues(pitch, roll, bodyTranslationY, legs);
    }

    public void apply(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        float animationBodyY = snapshots
                .get(config.bones().body())
                .map(snapshot -> applyBodyPose(snapshot, pose))
                .orElse(0.0F);
        for (DinosaurLegPose leg : pose.legs()) {
            applyLegPose(
                    config.leg(leg.legId()),
                    compensateBodyAnimation(config, leg, pose, animationBodyY),
                    snapshots);
        }
    }

    private static float applyBodyPose(BoneSnapshot body, DinosaurProceduralPose pose) {
        float animationBodyY = body.getTranslateY() / MODEL_UNITS_PER_BLOCK;
        body.setTranslateY(
                body.getTranslateY()
                        + pose.bodyTranslationYBlocks() * MODEL_UNITS_PER_BLOCK);
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
                applyRotation(snapshot, pose.footRotation()));
    }

    private static void applyRotation(
            BoneSnapshot snapshot,
            DinosaurBoneRotation rotation) {
        snapshot.setRotX(snapshot.getRotX() + rotation.xRadians());
        snapshot.setRotY(snapshot.getRotY() + rotation.yRadians());
        snapshot.setRotZ(snapshot.getRotZ() + rotation.zRadians());
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
        DinosaurLegPose withoutAnimation = DinosaurLegIkSolver.solveTarget(
                config,
                rig,
                smoothedLeg.targetFootHeightOffset(),
                pose.bodyTranslationYBlocks(),
                pose.pitchRadians(),
                pose.rollRadians(),
                smoothedLeg.planted());
        DinosaurLegPose withAnimation = DinosaurLegIkSolver.solveTarget(
                config,
                rig,
                smoothedLeg.targetFootHeightOffset(),
                pose.bodyTranslationYBlocks() + animationBodyY,
                pose.pitchRadians(),
                pose.rollRadians(),
                smoothedLeg.planted());
        return smoothedLeg.withRotations(
                addRotationDelta(
                        smoothedLeg.hipRotation(),
                        withoutAnimation.hipRotation(),
                        withAnimation.hipRotation()),
                addRotationDelta(
                        smoothedLeg.kneeRotation(),
                        withoutAnimation.kneeRotation(),
                        withAnimation.kneeRotation()),
                addRotationDelta(
                        smoothedLeg.footRotation(),
                        withoutAnimation.footRotation(),
                        withAnimation.footRotation()));
    }

    private static List<DinosaurLegPose> smoothLegs(
            List<DinosaurLegPose> previous,
            List<DinosaurLegPose> target,
            float alpha) {
        List<DinosaurLegPose> result = new ArrayList<>(target.size());
        for (DinosaurLegPose targetLeg : target) {
            DinosaurLegPose previousLeg = findLeg(previous, targetLeg);
            if (previousLeg == null) {
                result.add(targetLeg);
                continue;
            }
            result.add(targetLeg.withRotations(
                    lerpRotation(previousLeg.hipRotation(), targetLeg.hipRotation(), alpha),
                    lerpRotation(previousLeg.kneeRotation(), targetLeg.kneeRotation(), alpha),
                    lerpRotation(previousLeg.footRotation(), targetLeg.footRotation(), alpha)));
        }
        return List.copyOf(result);
    }

    private static DinosaurLegPose findLeg(
            List<DinosaurLegPose> legs,
            DinosaurLegPose target) {
        for (DinosaurLegPose leg : legs) {
            if (leg.legId().equals(target.legId())) {
                return leg;
            }
        }
        return null;
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

    private static DinosaurBoneRotation addRotationDelta(
            DinosaurBoneRotation base,
            DinosaurBoneRotation withoutAnimation,
            DinosaurBoneRotation withAnimation) {
        return new DinosaurBoneRotation(
                base.xRadians()
                        + angularDelta(
                                withoutAnimation.xRadians(),
                                withAnimation.xRadians()),
                base.yRadians()
                        + angularDelta(
                                withoutAnimation.yRadians(),
                                withAnimation.yRadians()),
                base.zRadians()
                        + angularDelta(
                                withoutAnimation.zRadians(),
                                withAnimation.zRadians()));
    }

    private static DinosaurBoneRotation lerpRotation(
            DinosaurBoneRotation start,
            DinosaurBoneRotation end,
            float alpha) {
        return new DinosaurBoneRotation(
                start.xRadians() + angularDelta(start.xRadians(), end.xRadians()) * alpha,
                start.yRadians() + angularDelta(start.yRadians(), end.yRadians()) * alpha,
                start.zRadians() + angularDelta(start.zRadians(), end.zRadians()) * alpha);
    }

    private record SmoothedPose(
            float pitch,
            float roll,
            float bodyTranslationY,
            List<DinosaurLegPose> legs,
            double renderTime) {
    }
}
