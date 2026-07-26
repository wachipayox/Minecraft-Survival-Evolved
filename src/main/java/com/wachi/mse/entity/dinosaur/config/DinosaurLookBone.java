package com.wachi.mse.entity.dinosaur.config;

/**
 * One element of a species' procedural look chain. Weights are local bone
 * rotations; because the bones are parented, their contributions accumulate
 * into the final head direction.
 */
public record DinosaurLookBone(
        String boneName,
        float yawWeight,
        float pitchWeight) {
    public DinosaurLookBone {
        if (boneName == null
                || boneName.isBlank()
                || yawWeight < 0.0F
                || pitchWeight < 0.0F) {
            throw new IllegalArgumentException("Procedural look bone values are invalid");
        }
    }
}
