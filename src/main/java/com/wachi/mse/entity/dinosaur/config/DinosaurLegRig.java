package com.wachi.mse.entity.dinosaur.config;

/**
 * Complete immutable description of one independently solved leg.
 *
 * <p>Coordinates and pivot heights are expressed in Blockbench model-space
 * blocks. Nothing in the procedural system assigns anatomical meaning to the
 * leg ID: a species can provide two, four, six or asymmetric legs and the
 * terrain plane is inferred from their configured positions.</p>
 */
public record DinosaurLegRig(
        String id,
        String shortName,
        String upperBone,
        String lowerBone,
        String footBone,
        double modelXOffset,
        double modelZOffset,
        double hipHeight,
        double kneeHeight,
        double footPivotHeight,
        float kneeBendDirection,
        float swingPhase) {
    public DinosaurLegRig {
        if (id == null
                || id.isBlank()
                || shortName == null
                || shortName.isBlank()
                || upperBone == null
                || upperBone.isBlank()
                || lowerBone == null
                || lowerBone.isBlank()
                || footBone == null
                || footBone.isBlank()) {
            throw new IllegalArgumentException("Leg ID, label and bone names are required");
        }
        if (!(hipHeight > kneeHeight && kneeHeight > footPivotHeight)) {
            throw new IllegalArgumentException("Leg pivots must descend from hip to foot");
        }
        if (kneeBendDirection != -1.0F && kneeBendDirection != 1.0F) {
            throw new IllegalArgumentException("Knee bend direction must be -1 or +1");
        }
        if (swingPhase < 0.0F || swingPhase >= 1.0F) {
            throw new IllegalArgumentException("Swing phase must be in [0, 1)");
        }
    }

    public double upperLength() {
        return this.hipHeight - this.kneeHeight;
    }

    public double lowerLength() {
        return this.kneeHeight - this.footPivotHeight;
    }

    /**
     * GeckoLib negates Blockbench/Bedrock X while baking geometry and pivots.
     */
    public double renderedModelXOffset() {
        return -this.modelXOffset;
    }
}
