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
        double footHalfWidth,
        double footHalfLength,
        float kneeBendDirection,
        float liftOffPhase,
        float apexPhase,
        float plantPhase) {
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
        if (!(hipHeight > kneeHeight && kneeHeight > footPivotHeight)
                || footHalfWidth <= 0.0
                || footHalfLength <= 0.0) {
            throw new IllegalArgumentException("Leg pivots must descend from hip to foot");
        }
        if (kneeBendDirection != -1.0F && kneeBendDirection != 1.0F) {
            throw new IllegalArgumentException("Knee bend direction must be -1 or +1");
        }
        if (!normalizedPhase(liftOffPhase)
                || !normalizedPhase(apexPhase)
                || !normalizedPhase(plantPhase)) {
            throw new IllegalArgumentException(
                    "Leg contact phases must be in [0, 1)");
        }
        float airborneDuration = positiveFraction(plantPhase - liftOffPhase);
        float apexProgress = positiveFraction(apexPhase - liftOffPhase);
        if (airborneDuration <= 0.0F
                || airborneDuration >= 1.0F
                || apexProgress <= 0.0F
                || apexProgress >= airborneDuration) {
            throw new IllegalArgumentException(
                    "Leg apex must lie between lift-off and plant");
        }
    }

    public double upperLength() {
        return this.hipHeight - this.kneeHeight;
    }

    public double lowerLength() {
        return this.kneeHeight - this.footPivotHeight;
    }

    public double totalLength() {
        return this.upperLength() + this.lowerLength();
    }

    /**
     * GeckoLib negates Blockbench/Bedrock X while baking geometry and pivots.
     */
    public double renderedModelXOffset() {
        return -this.modelXOffset;
    }

    private static boolean normalizedPhase(float phase) {
        return Float.isFinite(phase) && phase >= 0.0F && phase < 1.0F;
    }

    private static float positiveFraction(float value) {
        return value - (float) Math.floor(value);
    }
}
