package com.wachi.mse.client;

import com.wachi.mse.MseMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = MseMod.MOD_ID, dist = Dist.CLIENT)
public final class MseModClient {
    public MseModClient() {
        MseMod.LOGGER.info("Initializing Minecraft Survival Evolved client");
    }
}
