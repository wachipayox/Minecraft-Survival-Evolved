package com.wachi.mse.test.collide;

import com.wachi.mse.test.collide.terrain.TerrainWatcher;

import java.util.List;

public interface IGeometry {

    List<Capsule> getCollisions(double thickness);

    default boolean fitsInTerrain(TerrainWatcher watcher, double thickness) {
        var capsules = getCollisions(Math.max(0.0, thickness - getCollisionEpsilon()));

        for (Capsule capsule : capsules)
            for (var blockAABB : watcher.getCache()) {
                if (!capsule.bounds().intersects(blockAABB)) continue;
                var expanded = blockAABB.inflate(capsule.radius());
                if (
                        expanded.clip(capsule.start(), capsule.end()).isPresent()
                                || expanded.contains(capsule.start())
                                || expanded.contains(capsule.end())
                ) return false;
            }

        return true;
    }

    static double getCollisionEpsilon(){return 0.001;}
}
