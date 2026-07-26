package com.wachi.mse.entity.dinosaur.procedural;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain-derived posture shared by logical server code and client render
 * code. Terrain pitch/roll describe the measured slope; applied pitch/roll
 * contain only the limited hybrid body response. Displayed values may
 * additionally be smoothed by the renderer.
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
        float terrainPitchRadians,
        float terrainRollRadians,
        boolean pitchResolved,
        boolean rollResolved,
        DinosaurGaitState gait,
        List<DinosaurTerrainSample> samples,
        List<DinosaurLegPose> legs) {
    public DinosaurProceduralPose {
        samples = List.copyOf(samples);
        legs = List.copyOf(legs);
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

    public int reachableLegCount() {
        int count = 0;
        for (DinosaurLegPose leg : this.legs) {
            if (leg.reachable()) {
                count++;
            }
        }
        return count;
    }

    public DinosaurLegPose leg(String legId) {
        for (DinosaurLegPose leg : this.legs) {
            if (leg.legId().equals(legId)) {
                return leg;
            }
        }
        return null;
    }

    public DinosaurProceduralPose withSmoothedValues(
            float pitch,
            float roll,
            float bodyTranslationY,
            List<DinosaurLegPose> smoothedLegs) {
        return new DinosaurProceduralPose(
                this.origin,
                this.bodyYawDegrees,
                pitch,
                roll,
                bodyTranslationY,
                this.targetPitchRadians,
                this.targetRollRadians,
                this.targetBodyTranslationYBlocks,
                this.terrainPitchRadians,
                this.terrainRollRadians,
                this.pitchResolved,
                this.rollResolved,
                this.gait,
                this.samples,
                smoothedLegs);
    }
}
