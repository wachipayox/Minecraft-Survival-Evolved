package com.wachi.mse.test.collide;

import com.wachi.mse.test.collide.terrain.TerrainWatcher;

import java.util.List;

public interface IDinoGeometry {

    List<Collider> getCollisions();

    default boolean fitsInTerrain(TerrainWatcher watcher) {
        var colliders = getCollisions();

        for (Collider collider : colliders)
            if(collider.collidesWith(watcher.getCache()))
                return false;

        return true;
    }

    static double getCollisionEpsilon(){return 0.001;}
}
