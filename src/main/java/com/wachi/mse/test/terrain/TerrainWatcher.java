package com.wachi.mse.test.terrain;

import com.wachi.mse.MseMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TerrainWatcher {

    private Vec3 pivot;
    public AABB requiredArea, comfortArea, cachedArea;
    private final @Nullable Entity collisionContextEntity;
    private final double updateMargin;

    private boolean dirty = true;

    private List<AABB> cachedBlockCollisions;

    public TerrainWatcher(Vec3 pivot, AABB requiredArea, @Nullable Entity collisionContextEntity, double updateMargin) {
        this.pivot = pivot;
        this.updateMargin = updateMargin;
        this.requiredArea = requiredArea;
        this.collisionContextEntity = collisionContextEntity;
        updateMargins(null);
        dirty = true;
    }

    public void tick(Level level, Vec3 newPivotPos){
        requiredArea = requiredArea.move(newPivotPos.subtract(pivot));
        pivot = newPivotPos;

        if(cachedBlockCollisions == null || dirty || !isComfortable()) {
            updateMargins(level);
            cache(level);
        }
    }

    public void updateMargins(Level level){
        if(level instanceof ServerLevel sLevel)
            TerrainChangeTracker.unregisterWatcher(sLevel, this);

        comfortArea = requiredArea.inflate(updateMargin);
        cachedArea = comfortArea.inflate(updateMargin);
        dirty = false;

        if(level instanceof ServerLevel sLevel)
            TerrainChangeTracker.registerWatcher(sLevel, this);
    }

    private void cache(Level level){
        var list = new ArrayList<AABB>();
        for (VoxelShape blockCollision : level
                .getBlockCollisions(collisionContextEntity, cachedArea)
        ) {
            list.add(blockCollision.bounds());
        }
        cachedBlockCollisions = list;
        MseMod.LOGGER.debug("cached block collisions");
    }

    public boolean isComfortable(){
        return comfortArea.contains(requiredArea.getMinPosition())
                && comfortArea.contains(requiredArea.getMaxPosition());
    }

    public void setDirty(){
        dirty = true;
    }

    public List<AABB> getCache(){
        return cachedBlockCollisions;
    }
}
