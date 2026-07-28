package com.wachi.mse.registry;

import com.wachi.mse.MseMod;
import com.wachi.mse.entity.dinosaur.config.DinosaurProfile;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Synced datapack registry for procedural dinosaur profiles. Each concrete
 * registered entity class is permanently bound to one key from this registry.
 */
public final class MseDinosaurProfiles {
    public static final ResourceKey<Registry<DinosaurProfile>> REGISTRY =
            ResourceKey.createRegistryKey(id("dinosaur_profiles"));

    private MseDinosaurProfiles() {
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
                REGISTRY,
                DinosaurProfile.CODEC,
                DinosaurProfile.CODEC
        );
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MseMod.MOD_ID, path);
    }
}
