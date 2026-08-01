package com.wachi.mse.entity.dinosaur.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DinosaurProceduralConfig(
        String bodyBone,
        List<DinosaurLegRig> legs,
        GaitConfig gait,
        DinosaurStabilityConfig stability,
        DinosaurOrientationConfig orientation,
        DinosaurSkeletonConfig skeleton,
        DinosaurCombatConfig combat,
        DinosaurNavigationConfig navigation,
        float scale,
        double bodyPivotHeight,
        double footContactHeight,
        double contactPatchRadius,
        double sampleAbove,
        double sampleBelow,
        double maxBodyVerticalCorrection,
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
    public DinosaurProceduralConfig {
        legs = List.copyOf(legs);
        Set<String> legIds =
                legs.stream().map(DinosaurLegRig::id).collect(Collectors.toSet());
        if (bodyBone == null
                || bodyBone.isBlank()
                || gait == null
                || stability == null
                || orientation == null
                || skeleton == null
                || combat == null
                || navigation == null
                || !Float.isFinite(scale)
                || scale <= 0.0F
                || legs.size() < 2
                || legIds.size() != legs.size()
                || bodyPivotHeight <= footContactHeight
                || contactPatchRadius < 0.0
                || sampleAbove < 0.0
                || sampleBelow < 0.0
                || maxBodyVerticalCorrection < 0.0) {
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
                        leg.footLocalMinimum().scale(scale),
                        leg.footLocalMaximum().scale(scale),
                        leg.kneeBendDirection(),
                        leg.liftOffPhase(),
                        leg.apexPhase(),
                        leg.plantPhase()))
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
                this.bodyBone,
                scaledLegs,
                this.gait.scaled(scale),
                scaledStability,
                scaledOrientation,
                this.skeleton,
                this.combat,
                this.navigation,
                this.scale * scale,
                this.bodyPivotHeight * scale,
                this.footContactHeight * scale,
                this.contactPatchRadius * scale,
                this.sampleAbove * scale,
                this.sampleBelow * scale,
                this.maxBodyVerticalCorrection * scale,
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
            float strideLengthBlocks,
            float walkAnimationLengthSeconds,
            float fullActivitySpeed,
            float contactBlendFraction,
            float defaultAirborneFraction,
            int movementBlendTicks) {
        public GaitConfig {
            if (strideLengthBlocks <= 0.0F
                    || walkAnimationLengthSeconds <= 0.0F
                    || fullActivitySpeed <= 0.0F
                    || contactBlendFraction < 0.0F
                    || defaultAirborneFraction <= 0.0F
                    || defaultAirborneFraction >= 1.0F
                    || movementBlendTicks < 0
                    || contactBlendFraction * 2.0F
                            >= defaultAirborneFraction) {
                throw new IllegalArgumentException("Gait timing values are invalid");
            }
        }

        public float walkAnimationUnitsPerCycle() {
            return this.strideLengthBlocks * 4.0F;
        }

        public GaitConfig scaled(float scale) {
            return new GaitConfig(
                    this.strideLengthBlocks * scale,
                    this.walkAnimationLengthSeconds,
                    this.fullActivitySpeed,
                    this.contactBlendFraction,
                    this.defaultAirborneFraction,
                    this.movementBlendTicks);
        }
    }
}
