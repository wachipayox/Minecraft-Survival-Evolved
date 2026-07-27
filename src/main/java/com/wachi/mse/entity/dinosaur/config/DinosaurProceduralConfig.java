package com.wachi.mse.entity.dinosaur.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DinosaurProceduralConfig(
        DinosaurBoneNames bones,
        List<DinosaurLegRig> legs,
        GaitConfig gait,
        DinosaurStabilityConfig stability,
        DinosaurOrientationConfig orientation,
        DinosaurSkeletonConfig skeleton,
        DinosaurCombatConfig combat,
        DinosaurNavigationConfig navigation,
        float modelScale,
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
        float maxFootPitchRadians,
        float maxFootRollRadians,
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
                            1.5 / 16.0,
                            2.5 / 16.0,
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
                            1.5 / 16.0,
                            2.5 / 16.0,
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
                            1.5 / 16.0,
                            2.5 / 16.0,
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
                            1.5 / 16.0,
                            2.5 / 16.0,
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
                    0.008,
                    0.075,
                    16),
            new DinosaurOrientationConfig(
                    List.of(
                            new DinosaurLookBone("neck_1", 0.25F, 0.20F),
                            new DinosaurLookBone("neck_2", 0.35F, 0.35F),
                            new DinosaurLookBone("head", 0.40F, 0.45F)),
                    60.0F,
                    50.0F,
                    35.0F,
                    25.0F,
                    30.0F,
                    5.0F,
                    4.0F,
                    3.0F,
                    3.0F,
                    30.0F,
                    0.001,
                    0.35,
                    0.8,
                    4.0,
                    12.0F),
            DinosaurSkeletonConfig.PROTOTYPE,
            DinosaurCombatConfig.PROTOTYPE,
            DinosaurNavigationConfig.PROTOTYPE,
            1.0F,
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
            (float) Math.toRadians(25.0),
            (float) Math.toRadians(25.0),
            9.0F);
    public static final DinosaurProceduralConfig GIANT_PROTOTYPE =
            PROTOTYPE.scaled(10.0F);

    public DinosaurProceduralConfig {
        legs = List.copyOf(legs);
        Set<String> legIds =
                legs.stream().map(DinosaurLegRig::id).collect(Collectors.toSet());
        if (bones == null
                || gait == null
                || stability == null
                || orientation == null
                || skeleton == null
                || combat == null
                || navigation == null
                || !Float.isFinite(modelScale)
                || modelScale <= 0.0F
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
        if (!Float.isFinite(minLegReachFraction)
                || !Float.isFinite(maxLegReachFraction)
                || minLegReachFraction <= 0.0F
                || maxLegReachFraction >= 1.0F
                || minLegReachFraction >= maxLegReachFraction) {
            throw new IllegalArgumentException("Leg reach fractions are invalid");
        }
        if (maxPitchRadians < 0.0F
                || maxRollRadians < 0.0F
                || maxFootPitchRadians < 0.0F
                || maxFootRollRadians < 0.0F) {
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

    /**
     * Produces a geometrically similar species configuration without
     * duplicating its rig. Angles and normalized gait values remain
     * unchanged; every world-space distance is scaled uniformly.
     */
    public DinosaurProceduralConfig scaled(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            throw new IllegalArgumentException("Dinosaur scale must be positive and finite");
        }
        if (scale == 1.0F) {
            return this;
        }

        List<DinosaurLegRig> scaledLegs = this.legs.stream()
                .map(leg -> new DinosaurLegRig(
                        leg.id(),
                        leg.shortName(),
                        leg.upperBone(),
                        leg.lowerBone(),
                        leg.footBone(),
                        leg.modelXOffset() * scale,
                        leg.modelZOffset() * scale,
                        leg.hipHeight() * scale,
                        leg.kneeHeight() * scale,
                        leg.footPivotHeight() * scale,
                        leg.footHalfWidth() * scale,
                        leg.footHalfLength() * scale,
                        leg.kneeBendDirection(),
                        leg.swingPhase()))
                .toList();
        DinosaurStabilityConfig scaledStability =
                new DinosaurStabilityConfig(
                        this.stability.centerOfMassModelX() * scale,
                        this.stability.centerOfMassModelZ() * scale,
                        this.stability.footSupportRadius() * scale,
                        this.stability.awarenessBeyondReachLegLengths(),
                        this.stability.toleratedOutsideDistance() * scale,
                        this.stability.recoveryTicks(),
                        this.stability.maximumActivityForStaticBalance(),
                        this.stability.fallAccelerationPerTick() * scale,
                        this.stability.maximumFallHorizontalSpeed() * scale,
                        this.stability.airborneFallAssistTicks());
        DinosaurOrientationConfig scaledOrientation =
                new DinosaurOrientationConfig(
                        this.orientation.lookBones(),
                        this.orientation.maxNeckYawDegrees(),
                        this.orientation.bodyTurnStartYawDegrees(),
                        this.orientation.bodyTurnStopYawDegrees(),
                        this.orientation.maxPitchUpDegrees(),
                        this.orientation.maxPitchDownDegrees(),
                        this.orientation.headYawSpeedDegreesPerTick(),
                        this.orientation.headPitchSpeedDegreesPerTick(),
                        this.orientation.neckRecenteringSpeedDegreesPerTick(),
                        this.orientation.maxBodyYawChangeDegreesPerTick() / scale,
                        this.orientation.steeringDegreesPerBlock() / scale,
                        this.orientation.minimumTurningDistance() * scale,
                        this.orientation.lookTurnSpeedModifier(),
                        this.orientation.pathLookAheadRadiusMultiplier(),
                        this.orientation.maximumPathLookAheadBlocks() * scale,
                        this.orientation.visualSmoothingResponsePerSecond());
        return new DinosaurProceduralConfig(
                this.bones,
                scaledLegs,
                this.gait,
                scaledStability,
                scaledOrientation,
                this.skeleton,
                this.combat,
                this.navigation,
                this.modelScale * scale,
                this.bodyPivotHeight * scale,
                this.footContactHeight * scale,
                this.contactPatchRadius * scale,
                this.sampleAbove * scale,
                this.sampleBelow * scale,
                this.maxBodyVerticalCorrection * scale,
                this.swingFootLift * scale,
                this.minLegReachFraction,
                this.maxLegReachFraction,
                this.maxPitchRadians,
                this.maxRollRadians,
                this.slopeDeadzoneRadians,
                this.bodyTiltStartExtensionFraction,
                this.bodyTiltSlopeShare,
                this.maxHybridPitchRadians,
                this.maxHybridRollRadians,
                this.maxFootPitchRadians,
                this.maxFootRollRadians,
                this.smoothingResponsePerSecond);
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
