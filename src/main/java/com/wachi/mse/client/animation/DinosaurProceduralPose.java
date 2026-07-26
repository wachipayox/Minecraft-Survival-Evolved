package com.wachi.mse.client.animation;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public record DinosaurProceduralPose(
        Vec3 origin,
        float pitchRadians,
        float rollRadians,
        float targetPitchRadians,
        float targetRollRadians,
        boolean terrainValid,
        List<DinosaurTerrainSample> samples) {
    public DinosaurProceduralPose {
        samples = List.copyOf(samples);
    }

    public DinosaurProceduralPose withSmoothedAngles(float pitch, float roll) {
        return new DinosaurProceduralPose(
                this.origin,
                pitch,
                roll,
                this.targetPitchRadians,
                this.targetRollRadians,
                this.terrainValid,
                this.samples);
    }
}
