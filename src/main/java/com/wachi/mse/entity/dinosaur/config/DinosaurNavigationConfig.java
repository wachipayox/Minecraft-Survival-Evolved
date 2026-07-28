package com.wachi.mse.entity.dinosaur.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Navigation policy expressed mostly in body-relative units.
 */
public record DinosaurNavigationConfig(
        float maxVisitedNodesMultiplier,
        float localPlannerScaleThreshold,
        double localProbeBodyWidths,
        double minimumLocalProbeDistance,
        int steeringSamplesPerSide,
        float steeringSampleDegrees,
        int stuckTurnDelayTicks,
        float stuckTurnDegreesPerTick,
        double minimumProgressBlocks) {
    public static final Codec<DinosaurNavigationConfig> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("visited_nodes_multiplier", 3.0F)
                            .forGetter(DinosaurNavigationConfig::maxVisitedNodesMultiplier),
                    Codec.FLOAT.optionalFieldOf("local_planner_scale", 3.0F)
                            .forGetter(DinosaurNavigationConfig::localPlannerScaleThreshold),
                    Codec.DOUBLE.optionalFieldOf("probe_body_widths", 1.25)
                            .forGetter(DinosaurNavigationConfig::localProbeBodyWidths),
                    Codec.DOUBLE.optionalFieldOf("minimum_probe_distance", 2.0)
                            .forGetter(DinosaurNavigationConfig::minimumLocalProbeDistance),
                    Codec.INT.optionalFieldOf("steering_samples_per_side", 5)
                            .forGetter(DinosaurNavigationConfig::steeringSamplesPerSide),
                    Codec.FLOAT.optionalFieldOf("steering_sample_degrees", 18.0F)
                            .forGetter(DinosaurNavigationConfig::steeringSampleDegrees),
                    Codec.INT.optionalFieldOf("stuck_turn_delay", 8)
                            .forGetter(DinosaurNavigationConfig::stuckTurnDelayTicks),
                    Codec.FLOAT.optionalFieldOf("stuck_turn_degrees_per_tick", 3.0F)
                            .forGetter(DinosaurNavigationConfig::stuckTurnDegreesPerTick),
                    Codec.DOUBLE.optionalFieldOf("minimum_progress", 0.01)
                            .forGetter(DinosaurNavigationConfig::minimumProgressBlocks)
            ).apply(instance, DinosaurNavigationConfig::new));

    public static final DinosaurNavigationConfig DEFAULT =
            new DinosaurNavigationConfig(
                    3.0F,
                    3.0F,
                    1.25,
                    2.0,
                    5,
                    18.0F,
                    8,
                    3.0F,
                    0.01);

    public DinosaurNavigationConfig {
        if (!Float.isFinite(maxVisitedNodesMultiplier)
                || !Float.isFinite(localPlannerScaleThreshold)
                || !Double.isFinite(localProbeBodyWidths)
                || !Double.isFinite(minimumLocalProbeDistance)
                || !Float.isFinite(steeringSampleDegrees)
                || !Float.isFinite(stuckTurnDegreesPerTick)
                || !Double.isFinite(minimumProgressBlocks)
                || maxVisitedNodesMultiplier < 1.0F
                || localPlannerScaleThreshold <= 0.0F
                || localProbeBodyWidths <= 0.0
                || minimumLocalProbeDistance <= 0.0
                || steeringSamplesPerSide < 1
                || steeringSampleDegrees <= 0.0F
                || steeringSampleDegrees > 45.0F
                || stuckTurnDelayTicks < 1
                || stuckTurnDegreesPerTick <= 0.0F
                || minimumProgressBlocks < 0.0) {
            throw new IllegalArgumentException(
                    "Dinosaur navigation configuration is invalid");
        }
    }
}
