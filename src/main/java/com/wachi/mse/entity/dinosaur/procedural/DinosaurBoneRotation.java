package com.wachi.mse.entity.dinosaur.procedural;

/**
 * Additive GeckoLib bone rotation in radians.
 */
public record DinosaurBoneRotation(float xRadians, float yRadians, float zRadians) {
    public static final DinosaurBoneRotation ZERO =
            new DinosaurBoneRotation(0.0F, 0.0F, 0.0F);
}
