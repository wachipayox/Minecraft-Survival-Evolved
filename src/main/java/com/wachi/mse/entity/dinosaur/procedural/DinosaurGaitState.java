package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.GaitConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared distance-driven gait phase and continuous stance weights. GeckoLib
 * playback and logical support both consume this state.
 */
public record DinosaurGaitState(
        float phase,
        float activity,
        Map<String, Float> supportWeights) {
    public DinosaurGaitState {
        supportWeights = Map.copyOf(supportWeights);
    }

    public static DinosaurGaitState sampleInterpolated(
            LivingEntity entity,
            DinosaurProceduralConfig config,
            float partialTick) {
        return fromWalkAnimation(
                config,
                entity.walkAnimation.position(partialTick),
                entity.walkAnimation.speed(partialTick));
    }

    public static DinosaurGaitState sampleAuthoritative(
            LivingEntity entity,
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
        Map<String, Float> weights = new LinkedHashMap<>();

        for (DinosaurLegRig leg : config.legs()) {
            float phaseWeight = phaseSupportWeight(phase, leg, gait);
            weights.put(leg.id(), Mth.lerp(activity, 1.0F, phaseWeight));
        }

        return new DinosaurGaitState(phase, activity, weights);
    }

    public float supportWeight(String legId) {
        return this.supportWeights.getOrDefault(legId, 1.0F);
    }

    public float airborneWeight(String legId) {
        return 1.0F - this.supportWeight(legId);
    }

    private static float phaseSupportWeight(
            float gaitPhase,
            DinosaurLegRig leg,
            GaitConfig gait) {
        float airborneDuration = positiveFraction(
                leg.plantPhase() - leg.liftOffPhase());
        float airborneProgress = positiveFraction(
                gaitPhase - leg.liftOffPhase());
        float blend = Math.min(
                gait.contactBlendFraction(),
                airborneDuration * 0.5F);
        if (airborneProgress < airborneDuration) {
            if (blend <= 0.0F) {
                return 0.0F;
            }
            if (airborneProgress < blend) {
                return smoothStep(1.0F - airborneProgress / blend);
            }
            return 0.0F;
        }

        if (blend <= 0.0F) {
            return 1.0F;
        }
        float stanceProgress = positiveFraction(
                gaitPhase - leg.plantPhase());
        return stanceProgress < blend
                ? smoothStep(stanceProgress / blend)
                : 1.0F;
    }

    private static float positiveFraction(float value) {
        return value - Mth.floor(value);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
