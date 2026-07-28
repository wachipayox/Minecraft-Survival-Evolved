package com.wachi.mse.entity.dino;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class DinoCaprinoEntityRenderer extends EntityRenderer<DinoCaprinoEntity, LivingEntityRenderState> {

    public DinoCaprinoEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
