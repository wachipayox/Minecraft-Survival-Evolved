package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportPoint;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative terrain contact selected for one configured support point.
 *
 * <p>The X/Z coordinates can differ slightly from the nominal bone pivot when
 * the contact-patch fallback finds terrain under the edge of a foot.</p>
 */
public record DinosaurTerrainSample(
        SupportPoint point,
        Vec3 position,
        double heightOffset,
        boolean valid) {
}
