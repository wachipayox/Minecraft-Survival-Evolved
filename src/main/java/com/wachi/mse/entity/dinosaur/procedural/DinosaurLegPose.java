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
        boolean terrainContact,
        boolean planted,
        boolean reachable,
        boolean forcedMaximumExtension) {
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
                this.terrainContact,
                this.planted,
                this.reachable,
                this.forcedMaximumExtension);
    }

    public DinosaurLegPose withSupportState(
            boolean terrainContact,
            boolean planted,
            boolean reachable) {
        return new DinosaurLegPose(
                this.legId,
                this.hipRotation,
                this.kneeRotation,
                this.footRotation,
                this.targetFootHeightOffset,
                this.solvedFootHeightOffset,
                this.extensionFraction,
                terrainContact,
                planted,
                reachable,
                this.forcedMaximumExtension);
    }

    public DinosaurLegPose withForcedMaximumExtension() {
        return new DinosaurLegPose(
                this.legId,
                this.hipRotation,
                this.kneeRotation,
                this.footRotation,
                this.targetFootHeightOffset,
                this.solvedFootHeightOffset,
                this.extensionFraction,
                this.terrainContact,
                this.planted,
                this.reachable,
                true);
    }
}
