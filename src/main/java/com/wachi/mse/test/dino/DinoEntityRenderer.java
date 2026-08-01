package com.wachi.mse.test.dino;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class DinoEntityRenderer extends EntityRenderer<DinoEntity, LivingEntityRenderState> {

    public DinoEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
