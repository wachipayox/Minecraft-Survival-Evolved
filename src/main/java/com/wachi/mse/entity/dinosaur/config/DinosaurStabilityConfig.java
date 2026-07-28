package com.wachi.mse.entity.dinosaur.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Species-specific static balance and ledge recovery parameters.
 *
 * <p>The centre of mass uses Blockbench model-space X/Z coordinates. Search
 * depth grows from each leg's actual two-segment reach, while the remaining
 * values describe the finite support area under every planted foot and the
 * authoritative nudge used to leave an unstable ledge.</p>
 */
public record DinosaurStabilityConfig(
        double centerOfMassModelX,
        double centerOfMassModelZ,
        double footSupportRadius,
        double awarenessBeyondReachLegLengths,
        double toleratedOutsideDistance,
        int recoveryTicks,
        float maximumActivityForStaticBalance,
        double fallAccelerationPerTick,
        double maximumFallHorizontalSpeed,
        int airborneFallAssistTicks) {
    public static final DinosaurStabilityConfig DEFAULT =
            new DinosaurStabilityConfig(
                    0.0,
                    0.0,
                    2.0 / 16.0,
                    1.0,
                    1.0 / 16.0,
                    8,
                    0.02F,
                    0.008,
                    0.075,
                    16);
    public static final Codec<DinosaurStabilityConfig> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("center_of_mass_x", 0.0)
                            .forGetter(DinosaurStabilityConfig::centerOfMassModelX),
                    Codec.DOUBLE.optionalFieldOf("center_of_mass_z", 0.0)
                            .forGetter(DinosaurStabilityConfig::centerOfMassModelZ),
                    Codec.DOUBLE.optionalFieldOf("foot_support_radius", 2.0 / 16.0)
                            .forGetter(DinosaurStabilityConfig::footSupportRadius),
                    Codec.DOUBLE.optionalFieldOf("awareness_leg_lengths", 1.0)
                            .forGetter(DinosaurStabilityConfig::awarenessBeyondReachLegLengths),
                    Codec.DOUBLE.optionalFieldOf("outside_tolerance", 1.0 / 16.0)
                            .forGetter(DinosaurStabilityConfig::toleratedOutsideDistance),
                    Codec.INT.optionalFieldOf("recovery_ticks", 8)
                            .forGetter(DinosaurStabilityConfig::recoveryTicks),
                    Codec.FLOAT.optionalFieldOf("maximum_static_activity", 0.02F)
                            .forGetter(DinosaurStabilityConfig::maximumActivityForStaticBalance),
                    Codec.DOUBLE.optionalFieldOf("fall_acceleration", 0.008)
                            .forGetter(DinosaurStabilityConfig::fallAccelerationPerTick),
                    Codec.DOUBLE.optionalFieldOf("maximum_fall_speed", 0.075)
                            .forGetter(DinosaurStabilityConfig::maximumFallHorizontalSpeed),
                    Codec.INT.optionalFieldOf("airborne_assist_ticks", 16)
                            .forGetter(DinosaurStabilityConfig::airborneFallAssistTicks)
            ).apply(instance, DinosaurStabilityConfig::new));

    public DinosaurStabilityConfig {
        if (!Double.isFinite(centerOfMassModelX)
                || !Double.isFinite(centerOfMassModelZ)
                || !Double.isFinite(footSupportRadius)
                || !Double.isFinite(awarenessBeyondReachLegLengths)
                || !Double.isFinite(toleratedOutsideDistance)
                || !Float.isFinite(maximumActivityForStaticBalance)
                || !Double.isFinite(fallAccelerationPerTick)
                || !Double.isFinite(maximumFallHorizontalSpeed)
                || footSupportRadius <= 0.0
                || awarenessBeyondReachLegLengths < 0.0
                || toleratedOutsideDistance < 0.0
                || recoveryTicks < 1
                || airborneFallAssistTicks < 1
                || maximumActivityForStaticBalance < 0.0F
                || maximumActivityForStaticBalance > 1.0F
                || fallAccelerationPerTick <= 0.0
                || maximumFallHorizontalSpeed < fallAccelerationPerTick) {
            throw new IllegalArgumentException("Dinosaur stability values are invalid");
        }
    }
}
