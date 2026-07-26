package com.wachi.mse.registry;

import com.wachi.mse.MseMod;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MseEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES =
            DeferredRegister.createEntities(MseMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PrototypeDinosaurEntity>> PROTOTYPE_DINOSAUR =
            ENTITY_TYPES.registerEntityType(
                    "prototype_dinosaur",
                    PrototypeDinosaurEntity::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(1.8F, 2.4F)
                            .clientTrackingRange(12));

    private MseEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PROTOTYPE_DINOSAUR.get(), PrototypeDinosaurEntity.createAttributes().build());
    }
}
