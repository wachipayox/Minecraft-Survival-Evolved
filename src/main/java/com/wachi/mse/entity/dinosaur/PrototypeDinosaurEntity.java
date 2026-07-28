package com.wachi.mse.entity.dinosaur;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Concrete registered entity for the prototype species.
 */
public final class PrototypeDinosaurEntity extends DinosaurEntity {
    public PrototypeDinosaurEntity(
            EntityType<? extends PrototypeDinosaurEntity> entityType,
            Level level) {
        super(entityType, level);
    }
}
