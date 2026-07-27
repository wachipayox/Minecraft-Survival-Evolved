package com.wachi.mse.entity.dinosaur.procedural;

/**
 * Describes why an IK target did or did not fit inside a leg's anatomical
 * interval. Keeping the two failure directions separate is essential:
 * terrain below a leg may require maximum extension, while terrain too close
 * to the hip must never be answered by extending through the block.
 */
public enum DinosaurLegReachStatus {
    REACHABLE,
    /**
     * The target is inside the linkage's physical range but below the
     * species' preferred minimum extension. It is still a valid planted
     * contact; the extra knee fold is preferable to clipping through terrain.
     */
    COMPRESSED,
    TOO_CLOSE,
    TOO_FAR,
    TARGET_NOT_BELOW_HIP;

    public boolean reachable() {
        return this == REACHABLE || this == COMPRESSED;
    }

    public boolean needsMaximumExtension() {
        return this == TOO_FAR;
    }
}
