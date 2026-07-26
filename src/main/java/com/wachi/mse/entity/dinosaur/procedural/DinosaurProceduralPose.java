package com.wachi.mse.entity.dinosaur.procedural;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain-derived body pose shared by logical server code and client render
 * code. The target angles are deterministic for the supplied world snapshot;
 * the displayed angles may additionally be smoothed by the renderer.
 */
public record DinosaurProceduralPose(
        Vec3 origin,
        float bodyYawDegrees,
        float pitchRadians,
        float rollRadians,
        float bodyTranslationYBlocks,
        float targetPitchRadians,
        float targetRollRadians,
        float targetBodyTranslationYBlocks,
        boolean pitchResolved,
        boolean rollResolved,
        List<DinosaurTerrainSample> samples) {
    public DinosaurProceduralPose {
        samples = List.copyOf(samples);
    }

    public boolean terrainValid() {
        return this.pitchResolved || this.rollResolved;
    }

    public boolean fullyResolved() {
        return this.pitchResolved && this.rollResolved;
    }

    public int validSampleCount() {
        int count = 0;
        for (DinosaurTerrainSample sample : this.samples) {
            if (sample.valid()) {
                count++;
            }
        }
        return count;
    }

    public DinosaurProceduralPose withSmoothedValues(
            float pitch,
            float roll,
            float bodyTranslationY) {
        return new DinosaurProceduralPose(
                this.origin,
                this.bodyYawDegrees,
                pitch,
                roll,
                bodyTranslationY,
                this.targetPitchRadians,
                this.targetRollRadians,
                this.targetBodyTranslationYBlocks,
                this.pitchResolved,
                this.rollResolved,
                this.samples);
    }
}
