package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.GaitConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportPoint;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportProbe;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Shared gait phase and continuous stance weights. Future visual foot lift
 * must use this same phase so render contacts and logical contacts agree.
 */
public record DinosaurGaitState(
        float phase,
        float activity,
        Map<SupportPoint, Float> supportWeights) {
    public DinosaurGaitState {
        supportWeights = Map.copyOf(supportWeights);
    }

    public static DinosaurGaitState sampleInterpolated(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config,
            float partialTick) {
        return fromWalkAnimation(
                config,
                entity.walkAnimation.position(partialTick),
                entity.walkAnimation.speed(partialTick));
    }

    public static DinosaurGaitState sampleAuthoritative(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralConfig config) {
        return fromWalkAnimation(
                config,
                entity.walkAnimation.position(),
                entity.walkAnimation.speed());
    }

    public static DinosaurGaitState fromWalkAnimation(
            DinosaurProceduralConfig config,
            float walkPosition,
            float walkSpeed) {
        GaitConfig gait = config.gait();
        float phase = positiveFraction(walkPosition / gait.walkAnimationUnitsPerCycle());
        float activity = smoothStep(Mth.clamp(
                walkSpeed / gait.fullActivitySpeed(),
                0.0F,
                1.0F));
        Map<SupportPoint, Float> weights = new EnumMap<>(SupportPoint.class);

        for (SupportProbe probe : config.supportProbes()) {
            float phaseWeight = phaseSupportWeight(phase, probe.swingPhase(), gait);
            weights.put(probe.point(), Mth.lerp(activity, 1.0F, phaseWeight));
        }

        return new DinosaurGaitState(phase, activity, weights);
    }

    public float supportWeight(SupportPoint point) {
        return this.supportWeights.getOrDefault(point, 1.0F);
    }

    private static float phaseSupportWeight(
            float gaitPhase,
            float swingCenter,
            GaitConfig gait) {
        float distance = Math.abs(gaitPhase - swingCenter);
        distance = Math.min(distance, 1.0F - distance);
        float fullyLiftedHalfWidth = gait.fullyLiftedFraction() * 0.5F;
        if (distance <= fullyLiftedHalfWidth) {
            return 0.0F;
        }

        float blendEnd = fullyLiftedHalfWidth + gait.supportBlendFraction();
        if (distance >= blendEnd || gait.supportBlendFraction() == 0.0F) {
            return 1.0F;
        }

        float blend = (distance - fullyLiftedHalfWidth) / gait.supportBlendFraction();
        return smoothStep(blend);
    }

    private static float positiveFraction(float value) {
        return value - Mth.floor(value);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
