package com.wachi.mse.entity.dinosaur.procedural;

/**
 * IK bone rotation in radians relative to the neutral GeckoLib rig.
 */
public record DinosaurBoneRotation(float xRadians, float yRadians, float zRadians) {
    public static final DinosaurBoneRotation ZERO =
            new DinosaurBoneRotation(0.0F, 0.0F, 0.0F);
}
