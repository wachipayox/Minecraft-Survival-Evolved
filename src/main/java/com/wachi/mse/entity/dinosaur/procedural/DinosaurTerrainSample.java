package com.wachi.mse.entity.dinosaur.procedural;

import net.minecraft.world.phys.Vec3;

/**
 * Terrain contact selected for one configured support point.
 *
 * <p>The X/Z coordinates can differ slightly from the nominal bone pivot when
 * the contact-patch fallback finds terrain under the edge of a foot. If
 * {@link #valid} is false, the Y coordinate represents the configured lower
 * search boundary and therefore an inferred drop rather than real terrain.
 * {@link #supportWeight} is zero during full swing and one during full
 * stance.</p>
 */
public record DinosaurTerrainSample(
        String legId,
        String shortName,
        Vec3 position,
        double heightOffset,
        boolean valid,
        float supportWeight,
        float footPitchRadians,
        float footRollRadians) {
    private static final float PLANTED_SUPPORT_WEIGHT = 0.95F;

    public boolean plantedCandidate() {
        return this.valid && this.supportWeight >= PLANTED_SUPPORT_WEIGHT;
    }
}
