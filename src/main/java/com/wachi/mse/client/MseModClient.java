package com.wachi.mse.client;

import com.wachi.mse.MseMod;
import com.wachi.mse.client.renderer.PrototypeDinosaurRenderer;
import com.wachi.mse.registry.MseEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = MseMod.MOD_ID, dist = Dist.CLIENT)
public final class MseModClient {
    public MseModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MseEntities.PROTOTYPE_DINOSAUR.get(), PrototypeDinosaurRenderer::new);
    }
}
