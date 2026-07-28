package com.wachi.mse.client;

import com.wachi.mse.MseMod;
import com.wachi.mse.client.debug.DinosaurDebugRenderer;
import com.wachi.mse.client.renderer.DinosaurRenderer;
import com.wachi.mse.entity.dino.DinoCaprinoEntityRenderer;
import com.wachi.mse.registry.MseEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;

@Mod(value = MseMod.MOD_ID, dist = Dist.CLIENT)
public final class MseModClient {
    public MseModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerDebugRenderers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                MseEntities.PROTOTYPE_DINOSAUR.get(),
                DinosaurRenderer::new
        );
        event.registerEntityRenderer(
                MseEntities.DINO_CAPRINO.get(),
                DinoCaprinoEntityRenderer::new
        );
    }

    private void registerDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(DinosaurDebugRenderer::new);
    }
}
