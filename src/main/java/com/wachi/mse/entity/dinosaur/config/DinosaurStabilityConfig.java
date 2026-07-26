package com.wachi.mse.entity.dinosaur.config;

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
        double maximumFallHorizontalSpeed) {
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
                || maximumActivityForStaticBalance < 0.0F
                || maximumActivityForStaticBalance > 1.0F
                || fallAccelerationPerTick <= 0.0
                || maximumFallHorizontalSpeed < fallAccelerationPerTick) {
            throw new IllegalArgumentException("Dinosaur stability values are invalid");
        }
    }
}
