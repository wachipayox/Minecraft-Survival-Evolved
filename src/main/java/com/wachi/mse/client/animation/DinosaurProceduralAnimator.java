package com.wachi.mse.client.animation;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.Map;
import java.util.WeakHashMap;

public final class DinosaurProceduralAnimator {
    private static final double MAX_SMOOTHING_GAP_TICKS = 5.0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;

    private final Map<PrototypeDinosaurEntity, SmoothedPose> smoothedPoses = new WeakHashMap<>();

    public DinosaurProceduralPose smooth(
            PrototypeDinosaurEntity entity,
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
                    renderTime);
            this.smoothedPoses.put(entity, initial);
            return target.withSmoothedValues(
                    initial.pitch(),
                    initial.roll(),
                    initial.bodyTranslationY());
        }
        if (renderTime == previous.renderTime()) {
            return target.withSmoothedValues(
                    previous.pitch(),
                    previous.roll(),
                    previous.bodyTranslationY());
        }

        double elapsedSeconds = (renderTime - previous.renderTime()) / 20.0;
        float alpha = (float) (1.0 - Math.exp(-config.smoothingResponsePerSecond() * elapsedSeconds));
        float pitch = lerp(previous.pitch(), target.targetPitchRadians(), alpha);
        float roll = lerp(previous.roll(), target.targetRollRadians(), alpha);
        float bodyTranslationY = lerp(
                previous.bodyTranslationY(),
                target.targetBodyTranslationYBlocks(),
                alpha);
        this.smoothedPoses.put(
                entity,
                new SmoothedPose(pitch, roll, bodyTranslationY, renderTime));
        return target.withSmoothedValues(pitch, roll, bodyTranslationY);
    }

    public void apply(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        snapshots.get(config.bones().body()).ifPresent(snapshot -> applyBodyPose(snapshot, pose));
    }

    private static void applyBodyPose(BoneSnapshot body, DinosaurProceduralPose pose) {
        body.setTranslateY(
                body.getTranslateY()
                        + pose.bodyTranslationYBlocks() * MODEL_UNITS_PER_BLOCK);
        body.setRotX(body.getRotX() + pose.pitchRadians());
        body.setRotZ(body.getRotZ() + pose.rollRadians());
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    private record SmoothedPose(
            float pitch,
            float roll,
            float bodyTranslationY,
            double renderTime) {
    }
}
