package com.wachi.mse.client.animation;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig.SupportPoint;
import net.minecraft.world.phys.Vec3;

public record DinosaurTerrainSample(
        SupportPoint point,
        Vec3 position,
        double heightOffset,
        boolean valid) {
}
