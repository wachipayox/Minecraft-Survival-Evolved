package com.wachi.mse;

import com.wachi.mse.registry.MseEntities;
import com.wachi.mse.registry.MseDinosaurProfiles;
import com.wachi.mse.network.MseNetworking;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(MseMod.MOD_ID)
public final class MseMod {
    public static final String MOD_ID = "mc_evolved";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String NETWORK_VERSION = "1";

    public MseMod(IEventBus modEventBus) {
        MseEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(MseEntities::registerAttributes);
        modEventBus.addListener(MseDinosaurProfiles::register);
        modEventBus.addListener(MseNetworking::register);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Minecraft Survival Evolved");
    }
}
