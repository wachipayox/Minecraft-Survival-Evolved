package com.wachi.mse.test.dino.caprino;

import com.wachi.mse.test.dino.DinoEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class CaprinoEntity extends DinoEntity {

    public CaprinoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }
}
