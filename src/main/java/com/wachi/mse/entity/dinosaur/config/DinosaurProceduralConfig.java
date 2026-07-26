package com.wachi.mse.entity.dinosaur.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DinosaurProceduralConfig(
        DinosaurBoneNames bones,
        List<DinosaurLegRig> legs,
        GaitConfig gait,
        DinosaurStabilityConfig stability,
        double bodyPivotHeight,
        double footContactHeight,
        double contactPatchRadius,
        double sampleAbove,
        double sampleBelow,
        double maxBodyVerticalCorrection,
        double swingFootLift,
        float minLegReachFraction,
        float maxLegReachFraction,
        float maxPitchRadians,
        float maxRollRadians,
        float slopeDeadzoneRadians,
        float bodyTiltStartExtensionFraction,
        float bodyTiltSlopeShare,
        float maxHybridPitchRadians,
        float maxHybridRollRadians,
        float smoothingResponsePerSecond) {
    public static final DinosaurProceduralConfig PROTOTYPE = new DinosaurProceduralConfig(
            new DinosaurBoneNames("body"),
            List.of(
                    new DinosaurLegRig(
                            "front_left",
                            "FL",
                            "leg_front_left",
                            "shin_front_left",
                            "foot_front_left",
                            4.0 / 16.0,
                            -9.0 / 16.0,
                            12.0 / 16.0,
                            6.0 / 16.0,
                            1.0 / 16.0,
                            1.0F,
                            0.0F),
                    new DinosaurLegRig(
                            "front_right",
                            "FR",
                            "leg_front_right",
                            "shin_front_right",
                            "foot_front_right",
                            -4.0 / 16.0,
                            -9.0 / 16.0,
                            12.0 / 16.0,
                            6.0 / 16.0,
                            1.0 / 16.0,
                            1.0F,
                            0.5F),
                    new DinosaurLegRig(
                            "back_left",
                            "BL",
                            "leg_back_left",
                            "shin_back_left",
                            "foot_back_left",
                            4.0 / 16.0,
                            9.0 / 16.0,
                            12.0 / 16.0,
                            6.0 / 16.0,
                            1.0 / 16.0,
                            -1.0F,
                            0.75F),
                    new DinosaurLegRig(
                            "back_right",
                            "BR",
                            "leg_back_right",
                            "shin_back_right",
                            "foot_back_right",
                            -4.0 / 16.0,
                            9.0 / 16.0,
                            12.0 / 16.0,
                            6.0 / 16.0,
                            1.0 / 16.0,
                            -1.0F,
                            0.25F)),
            new GaitConfig(12.8F, 0.6F, 0.12F, 0.04F),
            new DinosaurStabilityConfig(
                    0.0,
                    0.0,
                    2.0 / 16.0,
                    1.0,
                    1.0 / 16.0,
                    8,
                    0.02F,
                    0.006,
                    0.055),
            14.0 / 16.0,
            0.0,
            2.0 / 16.0,
            1.25,
            0.75,
            0.75,
            2.5 / 16.0,
            0.35F,
            0.985F,
            (float) Math.toRadians(35.0),
            (float) Math.toRadians(15.0),
            (float) Math.toRadians(0.5),
            0.60F,
            0.35F,
            (float) Math.toRadians(10.0),
            (float) Math.toRadians(7.0),
            9.0F);

    public DinosaurProceduralConfig {
        legs = List.copyOf(legs);
        Set<String> legIds =
                legs.stream().map(DinosaurLegRig::id).collect(Collectors.toSet());
        if (bones == null
                || gait == null
                || stability == null
                || legs.size() < 2
                || legIds.size() != legs.size()
                || bodyPivotHeight <= footContactHeight
                || contactPatchRadius < 0.0
                || sampleAbove < 0.0
                || sampleBelow < 0.0
                || maxBodyVerticalCorrection < 0.0
                || swingFootLift < 0.0) {
            throw new IllegalArgumentException("Procedural terrain geometry values are invalid");
        }
        if (minLegReachFraction <= 0.0F
                || maxLegReachFraction >= 1.0F
                || minLegReachFraction >= maxLegReachFraction) {
            throw new IllegalArgumentException("Leg reach fractions are invalid");
        }
        if (maxPitchRadians < 0.0F || maxRollRadians < 0.0F) {
            throw new IllegalArgumentException("Procedural angle limits must be non-negative");
        }
        if (bodyTiltStartExtensionFraction <= minLegReachFraction
                || bodyTiltStartExtensionFraction >= maxLegReachFraction
                || bodyTiltSlopeShare < 0.0F
                || bodyTiltSlopeShare > 1.0F
                || maxHybridPitchRadians < 0.0F
                || maxHybridRollRadians < 0.0F) {
            throw new IllegalArgumentException("Hybrid body tilt values are invalid");
        }
        if (slopeDeadzoneRadians < 0.0F || smoothingResponsePerSecond <= 0.0F) {
            throw new IllegalArgumentException("Procedural smoothing values are invalid");
        }
    }

    public DinosaurLegRig leg(String id) {
        for (DinosaurLegRig rig : this.legs) {
            if (rig.id().equals(id)) {
                return rig;
            }
        }
        throw new IllegalArgumentException("No leg configured with ID " + id);
    }

    public record GaitConfig(
            float walkAnimationUnitsPerCycle,
            float fullActivitySpeed,
            float fullyLiftedFraction,
            float supportBlendFraction) {
        public GaitConfig {
            if (walkAnimationUnitsPerCycle <= 0.0F
                    || fullActivitySpeed <= 0.0F
                    || fullyLiftedFraction < 0.0F
                    || supportBlendFraction < 0.0F
                    || fullyLiftedFraction + 2.0F * supportBlendFraction >= 0.25F) {
                throw new IllegalArgumentException("Gait timing values are invalid");
            }
        }
    }
}
