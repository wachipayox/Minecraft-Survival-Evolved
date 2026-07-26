package com.wachi.mse.entity.dinosaur.procedural;

/**
 * Two-bone IK result for one leg. Rotations are additive GeckoLib XYZ values.
 */
public record DinosaurLegPose(
        String legId,
        DinosaurBoneRotation hipRotation,
        DinosaurBoneRotation kneeRotation,
        DinosaurBoneRotation footRotation,
        float targetFootHeightOffset,
        float solvedFootHeightOffset,
        float extensionFraction,
        boolean planted,
        boolean reachable) {
    public DinosaurLegPose withRotations(
            DinosaurBoneRotation hip,
            DinosaurBoneRotation knee,
            DinosaurBoneRotation foot) {
        return new DinosaurLegPose(
                this.legId,
                hip,
                knee,
                foot,
                this.targetFootHeightOffset,
                this.solvedFootHeightOffset,
                this.extensionFraction,
                this.planted,
                this.reachable);
    }
}
