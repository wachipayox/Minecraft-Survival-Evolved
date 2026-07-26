package com.wachi.mse.client.renderer;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.wachi.mse.MseMod;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public final class PrototypeDinosaurRenderer
        extends GeoEntityRenderer<PrototypeDinosaurEntity, LivingEntityRenderState> {

    public PrototypeDinosaurRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new DefaultedEntityGeoModel<>(
                        Identifier.fromNamespaceAndPath(MseMod.MOD_ID, "prototype_dinosaur")));
    }
}
