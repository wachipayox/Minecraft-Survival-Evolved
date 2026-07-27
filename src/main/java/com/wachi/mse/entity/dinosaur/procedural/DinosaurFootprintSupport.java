package com.wachi.mse.entity.dinosaur.procedural;

import net.minecraft.world.phys.Vec3;

/**
 * Area of the entity collision footprint that is still resting on terrain.
 *
 * <p>This is deliberately independent from anatomical foot contacts. A body
 * can remain caught on the edge of a block after every configured leg has
 * already lost support.</p>
 */
public record DinosaurFootprintSupport(
        double areaBlocksSquared,
        Vec3 centerWorld) {
    private static final double AREA_EPSILON = 1.0E-8;

    public DinosaurFootprintSupport {
        if (!Double.isFinite(areaBlocksSquared)
                || areaBlocksSquared < 0.0
                || centerWorld == null
                || !Double.isFinite(centerWorld.x)
                || !Double.isFinite(centerWorld.y)
                || !Double.isFinite(centerWorld.z)) {
            throw new IllegalArgumentException(
                    "Footprint support values are invalid");
        }
    }

    public static DinosaurFootprintSupport none() {
        return new DinosaurFootprintSupport(0.0, Vec3.ZERO);
    }

    public boolean present() {
        return this.areaBlocksSquared > AREA_EPSILON;
    }
}
