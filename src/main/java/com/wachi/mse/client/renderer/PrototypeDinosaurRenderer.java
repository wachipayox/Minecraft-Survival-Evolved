package com.wachi.mse.client.renderer;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.wachi.mse.MseMod;
import com.wachi.mse.client.animation.DinosaurProceduralAnimator;
import com.wachi.mse.client.debug.DinosaurDebugPoseStore;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSampler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class PrototypeDinosaurRenderer
        extends GeoEntityRenderer<PrototypeDinosaurEntity, LivingEntityRenderState> {
    private static final DataTicket<DinosaurProceduralPose> PROCEDURAL_POSE =
            DataTicket.create(
                    MseMod.MOD_ID + ":dinosaur_procedural_pose",
                    DinosaurProceduralPose.class);
    private static final DataTicket<DinosaurProceduralConfig> PROCEDURAL_CONFIG =
            DataTicket.create(
                    MseMod.MOD_ID + ":dinosaur_procedural_config",
                    DinosaurProceduralConfig.class);

    private final DinosaurProceduralAnimator proceduralAnimator = new DinosaurProceduralAnimator();

    public PrototypeDinosaurRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new DefaultedEntityGeoModel<>(
                        Identifier.fromNamespaceAndPath(MseMod.MOD_ID, "prototype_dinosaur")));
    }

    @Override
    protected AABB getBoundingBoxForCulling(PrototypeDinosaurEntity entity) {
        return entity.dinosaurVisualBounds();
    }

    @Override
    public void addRenderData(
            PrototypeDinosaurEntity animatable,
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
