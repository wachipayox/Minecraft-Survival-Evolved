package com.wachi.mse.entity.dinosaur.config;

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
    public static final DinosaurNavigationConfig PROTOTYPE =
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
