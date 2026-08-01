package com.wachi.mse.entity.dinosaur;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Concrete registered entity for the prototype species.
 */
public final class PrototypeDinosaurEntity extends DinosaurEntity {
    public PrototypeDinosaurEntity(
            EntityType<? extends PrototypeDinosaurEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return super.getPassengerRidingPosition(passenger)
                .add(0, -0.2 * getScale(), 0);
    }
}
