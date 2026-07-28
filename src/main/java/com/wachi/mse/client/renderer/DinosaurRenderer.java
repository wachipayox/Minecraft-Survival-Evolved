package com.wachi.mse.client.renderer;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.wachi.mse.MseMod;
import com.wachi.mse.client.animation.DinosaurProceduralAnimator;
import com.wachi.mse.client.debug.DinosaurDebugPoseStore;
import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSampler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class DinosaurRenderer<T extends DinosaurEntity>
        extends GeoEntityRenderer<T, LivingEntityRenderState> {
    private static final DataTicket<DinosaurProceduralPose> PROCEDURAL_POSE =
            DataTicket.create(
                    MseMod.MOD_ID + ":dinosaur_procedural_pose",
                    DinosaurProceduralPose.class);
    private static final DataTicket<DinosaurProceduralConfig> PROCEDURAL_CONFIG =
            DataTicket.create(
                    MseMod.MOD_ID + ":dinosaur_procedural_config",
                    DinosaurProceduralConfig.class);
    static final DataTicket<Identifier> MODEL =
            DataTicket.create(
                    MseMod.MOD_ID + ":dinosaur_model",
                    Identifier.class);

    private final DinosaurProceduralAnimator proceduralAnimator = new DinosaurProceduralAnimator();

    public DinosaurRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new DinosaurProfileGeoModel<>());
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        return entity.dinosaurVisualBounds();
    }

    @Override
    public void addRenderData(
            T animatable,
            @Nullable Void relatedObject,
            LivingEntityRenderState renderState,
            float partialTick) {
        DinosaurProceduralConfig config = animatable.proceduralConfig();
        DinosaurProceduralPose sampledPose =
                DinosaurTerrainSampler.sampleInterpolated(animatable, config, partialTick);
        DinosaurProceduralPose smoothedPose =
                this.proceduralAnimator.smooth(animatable, sampledPose, config, partialTick);
        ((GeoRenderState) renderState).addGeckolibData(PROCEDURAL_POSE, smoothedPose);
        ((GeoRenderState) renderState).addGeckolibData(PROCEDURAL_CONFIG, config);
        ((GeoRenderState) renderState).addGeckolibData(
                MODEL,
                animatable.dinosaurProfile().model());
        DinosaurDebugPoseStore.update(animatable, smoothedPose);
    }

    @Override
    public void adjustModelBonesForRender(
            RenderPassInfo<LivingEntityRenderState> renderPassInfo,
            BoneSnapshots snapshots) {
        DinosaurProceduralPose pose = renderPassInfo.getGeckolibData(PROCEDURAL_POSE);
        DinosaurProceduralConfig config =
                renderPassInfo.getGeckolibData(PROCEDURAL_CONFIG);
        if (pose != null && config != null) {
            this.proceduralAnimator.apply(
                    pose,
                    config,
                    snapshots);
        }
    }
}
