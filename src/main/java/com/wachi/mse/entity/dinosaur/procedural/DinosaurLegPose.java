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
        float footTerrainPitchRadians,
        float footTerrainRollRadians,
        boolean terrainContact,
        boolean planted,
        DinosaurLegReachStatus reachStatus,
        boolean forcedMaximumExtension) {
    public DinosaurLegPose {
        if (reachStatus == null) {
            throw new IllegalArgumentException("Leg reach status is required");
        }
    }

    public boolean reachable() {
        return this.reachStatus.reachable();
    }

    /**
     * Keeps the solved geometry from this pose while restoring the terrain
     * request that caused it. Maximum-extension recovery uses this so debug
     * output still describes the real floor target instead of replacing it
     * with the synthetic fully extended target.
     */
    public DinosaurLegPose withRequestedState(DinosaurLegPose requested) {
        if (!this.legId.equals(requested.legId)) {
            throw new IllegalArgumentException(
                    "Cannot copy contact state between different legs");
        }
        return new DinosaurLegPose(
                this.legId,
                this.hipRotation,
                this.kneeRotation,
                this.footRotation,
                requested.targetFootHeightOffset,
                this.solvedFootHeightOffset,
                this.extensionFraction,
                requested.footTerrainPitchRadians,
                requested.footTerrainRollRadians,
                requested.terrainContact,
                requested.planted,
                requested.reachStatus,
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
                this.footTerrainPitchRadians,
                this.footTerrainRollRadians,
                this.terrainContact,
                this.planted,
                this.reachStatus,
                true);
    }

    public DinosaurLegPose withFootTerrainTilt(
            float pitchRadians,
            float rollRadians) {
        return new DinosaurLegPose(
                this.legId,
                this.hipRotation,
                this.kneeRotation,
                this.footRotation,
                this.targetFootHeightOffset,
                this.solvedFootHeightOffset,
                this.extensionFraction,
                pitchRadians,
                rollRadians,
                this.terrainContact,
                this.planted,
                this.reachStatus,
                this.forcedMaximumExtension);
    }
}
