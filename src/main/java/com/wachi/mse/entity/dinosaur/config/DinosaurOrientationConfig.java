package com.wachi.mse.entity.dinosaur.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Species-specific neck limits and locomotor steering parameters.
 *
 * <p>The body turn is expressed both as a hard angular-speed cap and as
 * degrees per travelled block. The latter makes the turning radius depend on
 * actual displacement: a stationary dinosaur cannot pivot and a slow,
 * deliberate turn produces a tighter arc than a fast run.</p>
 */
public record DinosaurOrientationConfig(
        List<DinosaurLookBone> lookBones,
        float maxNeckYawDegrees,
        float bodyTurnStartYawDegrees,
        float bodyTurnStopYawDegrees,
        float maxPitchUpDegrees,
        float maxPitchDownDegrees,
        float headYawSpeedDegreesPerTick,
        float headPitchSpeedDegreesPerTick,
        float neckRecenteringSpeedDegreesPerTick,
        float maxBodyYawChangeDegreesPerTick,
        float steeringDegreesPerBlock,
        double minimumTurningDistance,
        double lookTurnSpeedModifier,
        double pathLookAheadRadiusMultiplier,
        double maximumPathLookAheadBlocks,
        float visualSmoothingResponsePerSecond) {
    private static final float WEIGHT_EPSILON = 1.0E-3F;

    public DinosaurOrientationConfig {
        lookBones = List.copyOf(lookBones);
        Set<String> names = lookBones.stream()
                .map(DinosaurLookBone::boneName)
                .collect(Collectors.toSet());
        float yawWeight = 0.0F;
        float pitchWeight = 0.0F;
        for (DinosaurLookBone bone : lookBones) {
            yawWeight += bone.yawWeight();
            pitchWeight += bone.pitchWeight();
        }

        if (lookBones.isEmpty()
                || names.size() != lookBones.size()
                || Math.abs(yawWeight - 1.0F) > WEIGHT_EPSILON
                || Math.abs(pitchWeight - 1.0F) > WEIGHT_EPSILON) {
            throw new IllegalArgumentException(
                    "Look bones must be unique and yaw/pitch weights must each total one");
        }
        if (!Double.isFinite(minimumTurningDistance)
                || !Double.isFinite(lookTurnSpeedModifier)
                || !Double.isFinite(pathLookAheadRadiusMultiplier)
                || !Double.isFinite(maximumPathLookAheadBlocks)
                || maxNeckYawDegrees <= 0.0F
                || maxNeckYawDegrees > 180.0F
                || bodyTurnStopYawDegrees < 0.0F
                || bodyTurnStopYawDegrees >= bodyTurnStartYawDegrees
                || bodyTurnStartYawDegrees > maxNeckYawDegrees
                || maxPitchUpDegrees < 0.0F
                || maxPitchDownDegrees < 0.0F
                || headYawSpeedDegreesPerTick <= 0.0F
                || headPitchSpeedDegreesPerTick <= 0.0F
                || neckRecenteringSpeedDegreesPerTick <= 0.0F
                || maxBodyYawChangeDegreesPerTick <= 0.0F
                || steeringDegreesPerBlock <= 0.0F
                || minimumTurningDistance < 0.0
                || lookTurnSpeedModifier <= 0.0
                || lookTurnSpeedModifier > 1.0
                || pathLookAheadRadiusMultiplier <= 0.0
                || maximumPathLookAheadBlocks <= 0.0
                || visualSmoothingResponsePerSecond <= 0.0F) {
            throw new IllegalArgumentException("Dinosaur orientation values are invalid");
        }
    }

    /**
     * Smallest arc radius allowed at the supplied horizontal speed after both
     * angular constraints have been applied.
     */
    public double turningRadiusBlocks(double speedBlocksPerTick) {
        if (speedBlocksPerTick <= 0.0) {
            return 0.0;
        }
        double degreesPerTick = Math.min(
                this.maxBodyYawChangeDegreesPerTick,
                speedBlocksPerTick * this.steeringDegreesPerBlock);
        return degreesPerTick <= 0.0
                ? 0.0
                : speedBlocksPerTick / Math.toRadians(degreesPerTick);
    }
}
