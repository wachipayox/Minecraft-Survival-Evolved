package com.wachi.mse.client.renderer;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.wachi.mse.MseMod;
import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import net.minecraft.resources.Identifier;

/**
 * Resolves GeckoLib resources from the concrete entity's datapack profile.
 */
final class DinosaurProfileGeoModel<T extends DinosaurEntity>
        extends DefaultedEntityGeoModel<T> {
    private static final Identifier FALLBACK =
            Identifier.fromNamespaceAndPath(
                    MseMod.MOD_ID,
                    "prototype_dinosaur");

    DinosaurProfileGeoModel() {
        super(FALLBACK);
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        Identifier model = renderState.getGeckolibData(
                DinosaurRenderer.MODEL);
        return this.buildFormattedModelPath(
                model == null ? FALLBACK : model);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        Identifier model = renderState.getGeckolibData(
                DinosaurRenderer.MODEL);
        return this.buildFormattedTexturePath(
                model == null ? FALLBACK : model);
    }

    @Override
    public Identifier getAnimationResource(
            T animatable) {
        return this.buildFormattedAnimationPath(
                animatable.dinosaurProfile().model());
    }
}
