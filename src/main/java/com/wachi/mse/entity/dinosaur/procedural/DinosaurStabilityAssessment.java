package com.wachi.mse.entity.dinosaur.procedural;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Geometric result of projecting the configured centre of mass onto the
 * finite support polygon formed by reachable, planted feet.
 */
public record DinosaurStabilityAssessment(
        boolean evaluable,
        boolean stable,
        double signedMarginBlocks,
        Vec3 centerOfMassWorld,
        Vec3 fallDirectionWorld,
        int supportingLegCount,
        List<Vec3> supportHull,
        DinosaurFootprintSupport footprintSupport) {
    public DinosaurStabilityAssessment {
        supportHull = List.copyOf(supportHull);
        if (footprintSupport == null) {
            throw new IllegalArgumentException(
                    "Footprint support is required");
        }
    }

    public static DinosaurStabilityAssessment notEvaluable(
            Vec3 centerOfMassWorld,
            int supportingLegCount,
            DinosaurFootprintSupport footprintSupport) {
        return new DinosaurStabilityAssessment(
                false,
                false,
                Double.NEGATIVE_INFINITY,
                centerOfMassWorld,
                Vec3.ZERO,
                supportingLegCount,
                List.of(),
                footprintSupport);
    }

    public static DinosaurStabilityAssessment fullyUnsupported(
            Vec3 centerOfMassWorld,
            Vec3 fallDirectionWorld,
            DinosaurFootprintSupport footprintSupport) {
        return new DinosaurStabilityAssessment(
                true,
                false,
                Double.NEGATIVE_INFINITY,
                centerOfMassWorld,
                fallDirectionWorld,
                0,
                List.of(),
                footprintSupport);
    }

    public boolean requiresRecovery() {
        return this.evaluable && !this.stable;
    }
}
