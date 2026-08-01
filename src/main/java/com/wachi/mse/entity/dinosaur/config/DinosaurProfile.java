package com.wachi.mse.entity.dinosaur.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Serializable dinosaur profile. This is the only configuration type a
 * datapack author needs to know; the remaining config records are runtime
 * details used by the procedural solvers.
 */
public record DinosaurProfile(
        Identifier model,
        Animations animations,
        String bodyBone,
        List<String> lookChain,
        List<Leg> legs,
        DinosaurSkeletonConfig skeleton,
        DinosaurCombatConfig combat,
        DinosaurStabilityConfig stability,
        DinosaurNavigationConfig navigation,
        Stats stats,
        Gait gait,
        Orientation orientation,
        Terrain terrain,
        LegMotion legMotion,
        boolean canBeWalkedOn) {
    public static final Codec<DinosaurProfile> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("model")
                            .forGetter(DinosaurProfile::model),
                    Animations.CODEC.optionalFieldOf(
                                    "animations",
                                    Animations.DEFAULT)
                            .forGetter(DinosaurProfile::animations),
                    Codec.STRING.optionalFieldOf("body_bone", "body")
                            .forGetter(DinosaurProfile::bodyBone),
                    Codec.STRING.listOf().fieldOf("look_chain")
                            .forGetter(DinosaurProfile::lookChain),
                    Leg.CODEC.listOf().fieldOf("legs")
                            .forGetter(DinosaurProfile::legs),
                    DinosaurSkeletonConfig.CODEC.fieldOf("skeleton")
                            .forGetter(DinosaurProfile::skeleton),
                    DinosaurCombatConfig.CODEC.optionalFieldOf(
                                    "combat",
                                    new DinosaurCombatConfig(List.of()))
                            .forGetter(DinosaurProfile::combat),
                    DinosaurStabilityConfig.CODEC.optionalFieldOf(
                                    "stability",
                                    DinosaurStabilityConfig.DEFAULT)
                            .forGetter(DinosaurProfile::stability),
                    DinosaurNavigationConfig.CODEC.optionalFieldOf(
                                    "navigation",
                                    DinosaurNavigationConfig.DEFAULT)
                            .forGetter(DinosaurProfile::navigation),
                    Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT)
                            .forGetter(DinosaurProfile::stats),
                    Gait.CODEC.optionalFieldOf("gait", Gait.DEFAULT)
                            .forGetter(DinosaurProfile::gait),
                    Orientation.CODEC.optionalFieldOf(
                                    "orientation",
                                    Orientation.DEFAULT)
                            .forGetter(DinosaurProfile::orientation),
                    Terrain.CODEC.optionalFieldOf("terrain", Terrain.DEFAULT)
                            .forGetter(DinosaurProfile::terrain),
                    LegMotion.CODEC.optionalFieldOf(
                                    "leg_motion",
                                    LegMotion.DEFAULT)
                            .forGetter(DinosaurProfile::legMotion),
                    Codec.BOOL.optionalFieldOf("can_be_walked_on", false)
                            .forGetter(DinosaurProfile::canBeWalkedOn)
            ).apply(instance, DinosaurProfile::new));

    public DinosaurProfile {
        lookChain = List.copyOf(lookChain);
        legs = List.copyOf(legs);
        if (model == null
                || bodyBone.isBlank()
                || lookChain.isEmpty()
                || legs.size() < 2) {
            throw new IllegalArgumentException(
                    "A dinosaur needs a model, a body, a look chain and at least two legs");
        }
    }

    public DinosaurProceduralConfig createBaseConfig() {
        List<DinosaurLegRig> resolvedLegs = new ArrayList<>(this.legs.size());
        for (int index = 0; index < this.legs.size(); index++) {
            resolvedLegs.add(this.legs.get(index).resolve(
                    this.skeleton,
                    this.gait,
                    index));
        }

        float weightSum = this.lookChain.size()
                * (this.lookChain.size() + 1.0F)
                * 0.5F;
        List<DinosaurLookBone> lookBones = new ArrayList<>(this.lookChain.size());
        for (int index = 0; index < this.lookChain.size(); index++) {
            float weight = (index + 1.0F) / weightSum;
            lookBones.add(new DinosaurLookBone(
                    this.lookChain.get(index),
                    weight,
                    weight));
        }

        return new DinosaurProceduralConfig(
                this.bodyBone,
                resolvedLegs,
                this.gait.toConfig(),
                this.stability,
                this.orientation.toConfig(lookBones),
                this.skeleton,
                this.combat,
                this.navigation,
                1.0F,
                this.terrain.bodyPivotHeight,
                this.terrain.footContactHeight,
                this.terrain.contactPatchRadius,
                this.terrain.sampleAbove,
                this.terrain.sampleBelow,
                this.terrain.maxBodyVerticalCorrection,
                this.legMotion.minReachFraction,
                this.legMotion.maxReachFraction,
                radians(this.terrain.maxPitchDegrees),
                radians(this.terrain.maxRollDegrees),
                radians(this.terrain.slopeDeadzoneDegrees),
                this.legMotion.bodyTiltStartFraction,
                this.legMotion.bodyTiltSlopeShare,
                radians(this.legMotion.maxHybridPitchDegrees),
                radians(this.legMotion.maxHybridRollDegrees),
                radians(this.legMotion.maxFootPitchDegrees),
                radians(this.legMotion.maxFootRollDegrees),
                this.legMotion.smoothingResponse);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    public String idleAnimation() {
        return this.animations.resolve(
                this.model,
                this.animations.idle,
                "idle");
    }

    public String walkAnimation() {
        return this.animations.resolve(
                this.model,
                this.animations.walk,
                "walk");
    }

    public record Animations(String idle, String walk) {
        public static final Animations DEFAULT = new Animations("", "");
        public static final Codec<Animations> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.optionalFieldOf("idle", "")
                                .forGetter(Animations::idle),
                        Codec.STRING.optionalFieldOf("walk", "")
                                .forGetter(Animations::walk)
                ).apply(instance, Animations::new));

        private String resolve(
                Identifier model,
                String configured,
                String fallbackSuffix) {
            return configured.isBlank()
                    ? "animation."
                            + model.getPath().replace('/', '.')
                            + "."
                            + fallbackSuffix
                    : configured;
        }
    }

    public record Leg(
            String id,
            String upperBone,
            String lowerBone,
            String footBone,
            float kneeBend,
            float gaitPhase,
            Optional<ContactTiming> contact) {
        public static final Codec<Leg> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("id").forGetter(Leg::id),
                        Codec.STRING.fieldOf("upper").forGetter(Leg::upperBone),
                        Codec.STRING.fieldOf("lower").forGetter(Leg::lowerBone),
                        Codec.STRING.fieldOf("foot").forGetter(Leg::footBone),
                        Codec.FLOAT.optionalFieldOf("knee_bend", 0.0F)
                                .forGetter(Leg::kneeBend),
                        Codec.FLOAT.optionalFieldOf("gait_phase", -1.0F)
                                .forGetter(Leg::gaitPhase),
                        ContactTiming.CODEC.optionalFieldOf("contact")
                                .forGetter(Leg::contact)
                ).apply(instance, Leg::new));

        private DinosaurLegRig resolve(
                DinosaurSkeletonConfig skeleton,
                Gait gait,
                int index) {
            Vec3 hip = skeleton.bone(this.upperBone).pivot();
            Vec3 knee = skeleton.bone(this.lowerBone).pivot();
            Vec3 foot = skeleton.bone(this.footBone).pivot();
            List<DinosaurSkeletonConfig.BoneBox> footBoxes = skeleton.hitboxParts()
                    .stream()
                    .flatMap(part -> part.boxes().stream())
                    .filter(box -> box.boneName().equals(this.footBone))
                    .toList();
            if (footBoxes.isEmpty()) {
                throw new IllegalArgumentException(
                        "No hitbox box exists for foot bone " + this.footBone);
            }
            Vec3 boxMinimum = new Vec3(
                    footBoxes.stream()
                            .mapToDouble(box -> box.minimum().x)
                            .min()
                            .orElseThrow(),
                    footBoxes.stream()
                            .mapToDouble(box -> box.minimum().y)
                            .min()
                            .orElseThrow(),
                    footBoxes.stream()
                            .mapToDouble(box -> box.minimum().z)
                            .min()
                            .orElseThrow());
            Vec3 boxMaximum = new Vec3(
                    footBoxes.stream()
                            .mapToDouble(box -> box.maximum().x)
                            .max()
                            .orElseThrow(),
                    footBoxes.stream()
                            .mapToDouble(box -> box.maximum().y)
                            .max()
                            .orElseThrow(),
                    footBoxes.stream()
                            .mapToDouble(box -> box.maximum().z)
                            .max()
                            .orElseThrow());
            // GeckoLib mirrors Blockbench X while baking. Store the box in
            // the same local rendered coordinate system used by the solver.
            Vec3 localMinimum = new Vec3(
                    -(boxMaximum.x - foot.x),
                    boxMinimum.y - foot.y,
                    boxMinimum.z - foot.z);
            Vec3 localMaximum = new Vec3(
                    -(boxMinimum.x - foot.x),
                    boxMaximum.y - foot.y,
                    boxMaximum.z - foot.z);
            double halfWidth =
                    (localMaximum.x - localMinimum.x) * 0.5;
            double halfLength =
                    (localMaximum.z - localMinimum.z) * 0.5;
            float bend = this.kneeBend == 0.0F
                    ? (foot.z <= 0.0 ? 1.0F : -1.0F)
                    : Math.signum(this.kneeBend);
            float phase = this.gaitPhase < 0.0F
                    ? defaultGaitPhase(foot, index)
                    : this.gaitPhase;
            ContactTiming timing = this.contact.orElseGet(() ->
                    ContactTiming.centered(
                            phase,
                            gait.defaultAirborneFraction));
            return new DinosaurLegRig(
                    this.id,
                    shortName(this.id),
                    this.upperBone,
                    this.lowerBone,
                    this.footBone,
                    foot.x,
                    foot.z,
                    hip.y,
                    knee.y,
                    foot.y,
                    halfWidth,
                    halfLength,
                    localMinimum,
                    localMaximum,
                    bend,
                    timing.liftOff,
                    timing.apex,
                    timing.plant);
        }

        private static float defaultGaitPhase(Vec3 foot, int index) {
            if (Math.abs(foot.x) > 1.0E-4
                    && Math.abs(foot.z) > 1.0E-4) {
                return (foot.x < 0.0) == (foot.z < 0.0)
                        ? 0.5F
                        : 0.0F;
            }
            return (index & 1) == 0 ? 0.0F : 0.5F;
        }

        private static String shortName(String id) {
            StringBuilder result = new StringBuilder(3);
            for (String word : id.split("_")) {
                if (!word.isBlank()) {
                    result.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return result.isEmpty()
                    ? id.substring(0, Math.min(2, id.length()))
                            .toUpperCase(Locale.ROOT)
                    : result.toString();
        }
    }

    public record ContactTiming(
            float liftOff,
            float apex,
            float plant) {
        public static final Codec<ContactTiming> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.fieldOf("lift_off")
                                .forGetter(ContactTiming::liftOff),
                        Codec.FLOAT.fieldOf("apex")
                                .forGetter(ContactTiming::apex),
                        Codec.FLOAT.fieldOf("plant")
                                .forGetter(ContactTiming::plant)
                ).apply(instance, ContactTiming::new));

        public ContactTiming {
            if (!normalized(liftOff)
                    || !normalized(apex)
                    || !normalized(plant)) {
                throw new IllegalArgumentException(
                        "Contact phases must be in [0, 1)");
            }
            float airborneDuration = positiveFraction(plant - liftOff);
            float apexProgress = positiveFraction(apex - liftOff);
            if (airborneDuration <= 0.0F
                    || airborneDuration >= 1.0F
                    || apexProgress <= 0.0F
                    || apexProgress >= airborneDuration) {
                throw new IllegalArgumentException(
                        "Contact apex must lie between lift-off and plant");
            }
        }

        private static ContactTiming centered(
                float apex,
                float airborneFraction) {
            float halfAirborne = airborneFraction * 0.5F;
            return new ContactTiming(
                    positiveFraction(apex - halfAirborne),
                    apex,
                    positiveFraction(apex + halfAirborne));
        }

        private static boolean normalized(float phase) {
            return Float.isFinite(phase)
                    && phase >= 0.0F
                    && phase < 1.0F;
        }

        private static float positiveFraction(float value) {
            return value - (float) Math.floor(value);
        }
    }

    public record Gait(
            float strideLengthBlocks,
            float animationLengthSeconds,
            float fullActivitySpeed,
            float contactBlendFraction,
            float defaultAirborneFraction,
            int movementBlendTicks) {
        public static final Gait DEFAULT =
                new Gait(3.2F, 1.0F, 0.6F, 0.04F, 0.25F, 6);
        public static final Codec<Gait> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf(
                                        "stride_length",
                                        DEFAULT.strideLengthBlocks)
                                .forGetter(Gait::strideLengthBlocks),
                        Codec.FLOAT.optionalFieldOf(
                                        "animation_length",
                                        DEFAULT.animationLengthSeconds)
                                .forGetter(Gait::animationLengthSeconds),
                        Codec.FLOAT.optionalFieldOf(
                                        "full_activity_speed",
                                        DEFAULT.fullActivitySpeed)
                                .forGetter(Gait::fullActivitySpeed),
                        Codec.FLOAT.optionalFieldOf(
                                        "contact_blend_fraction",
                                        DEFAULT.contactBlendFraction)
                                .forGetter(Gait::contactBlendFraction),
                        Codec.FLOAT.optionalFieldOf(
                                        "default_airborne_fraction",
                                        DEFAULT.defaultAirborneFraction)
                                .forGetter(Gait::defaultAirborneFraction),
                        Codec.INT.optionalFieldOf(
                                        "movement_blend_ticks",
                                        DEFAULT.movementBlendTicks)
                                .forGetter(Gait::movementBlendTicks)
                ).apply(instance, Gait::new));

        private DinosaurProceduralConfig.GaitConfig toConfig() {
            return new DinosaurProceduralConfig.GaitConfig(
                    this.strideLengthBlocks,
                    this.animationLengthSeconds,
                    this.fullActivitySpeed,
                    this.contactBlendFraction,
                    this.defaultAirborneFraction,
                    this.movementBlendTicks);
        }
    }

    /**
     * Generic effective stats. SCALE intentionally is not a profile value:
     * it remains a live vanilla attribute that can change at runtime.
     */
    public record Stats(
            double maxHealth,
            double movementSpeed,
            double attackDamage,
            double followRange,
            double stepHeight,
            Optional<Double> ridingCameraDistance) {
        public static final Stats DEFAULT =
                new Stats(
                        40.0,
                        0.20,
                        6.0,
                        24.0,
                        0.6,
                        Optional.empty());
        public static final Codec<Stats> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.DOUBLE.optionalFieldOf("max_health", DEFAULT.maxHealth)
                                .forGetter(Stats::maxHealth),
                        Codec.DOUBLE.optionalFieldOf("movement_speed", DEFAULT.movementSpeed)
                                .forGetter(Stats::movementSpeed),
                        Codec.DOUBLE.optionalFieldOf("attack_damage", DEFAULT.attackDamage)
                                .forGetter(Stats::attackDamage),
                        Codec.DOUBLE.optionalFieldOf("follow_range", DEFAULT.followRange)
                                .forGetter(Stats::followRange),
                        Codec.DOUBLE.optionalFieldOf("step_height", DEFAULT.stepHeight)
                                .forGetter(Stats::stepHeight),
                        Codec.DOUBLE.optionalFieldOf(
                                        "riding_camera_distance")
                                .forGetter(Stats::ridingCameraDistance)
                ).apply(instance, Stats::new));

        public Stats {
            ridingCameraDistance =
                    ridingCameraDistance == null
                            ? Optional.empty()
                            : ridingCameraDistance;
            if (maxHealth <= 0.0
                    || movementSpeed < 0.0
                    || attackDamage < 0.0
                    || followRange <= 0.0
                    || stepHeight < 0.0
                    || ridingCameraDistance.stream()
                            .anyMatch(distance ->
                                    !Double.isFinite(distance)
                                            || distance < 0.0
                                            || distance > 32.0)) {
                throw new IllegalArgumentException(
                        "Dinosaur generic stats are invalid");
            }
        }
    }

    public record Orientation(
            float maxNeckYaw,
            float bodyTurnStartYaw,
            float bodyTurnStopYaw,
            float maxPitchUp,
            float maxPitchDown,
            float headYawSpeed,
            float headPitchSpeed,
            float neckRecenteringSpeed,
            float maxBodyYawChange,
            float steeringDegreesPerBlock,
            double minimumTurningDistance,
            double lookTurnSpeedModifier,
            double pathLookAheadRadiusMultiplier,
            double maximumPathLookAhead,
            float visualSmoothingResponse) {
        public static final Orientation DEFAULT = new Orientation(
                60, 50, 35, 25, 30, 5, 4, 3, 3, 30,
                0.001, 1.0, 0.8, 4.0, 12);
        public static final Codec<Orientation> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("max_neck_yaw", DEFAULT.maxNeckYaw).forGetter(Orientation::maxNeckYaw),
                        Codec.FLOAT.optionalFieldOf("body_turn_start_yaw", DEFAULT.bodyTurnStartYaw).forGetter(Orientation::bodyTurnStartYaw),
                        Codec.FLOAT.optionalFieldOf("body_turn_stop_yaw", DEFAULT.bodyTurnStopYaw).forGetter(Orientation::bodyTurnStopYaw),
                        Codec.FLOAT.optionalFieldOf("max_pitch_up", DEFAULT.maxPitchUp).forGetter(Orientation::maxPitchUp),
                        Codec.FLOAT.optionalFieldOf("max_pitch_down", DEFAULT.maxPitchDown).forGetter(Orientation::maxPitchDown),
                        Codec.FLOAT.optionalFieldOf("head_yaw_speed", DEFAULT.headYawSpeed).forGetter(Orientation::headYawSpeed),
                        Codec.FLOAT.optionalFieldOf("head_pitch_speed", DEFAULT.headPitchSpeed).forGetter(Orientation::headPitchSpeed),
                        Codec.FLOAT.optionalFieldOf("neck_recentering_speed", DEFAULT.neckRecenteringSpeed).forGetter(Orientation::neckRecenteringSpeed),
                        Codec.FLOAT.optionalFieldOf("max_body_yaw_change", DEFAULT.maxBodyYawChange).forGetter(Orientation::maxBodyYawChange),
                        Codec.FLOAT.optionalFieldOf("steering_degrees_per_block", DEFAULT.steeringDegreesPerBlock).forGetter(Orientation::steeringDegreesPerBlock),
                        Codec.DOUBLE.optionalFieldOf("minimum_turning_distance", DEFAULT.minimumTurningDistance).forGetter(Orientation::minimumTurningDistance),
                        Codec.DOUBLE.optionalFieldOf("look_turn_speed_modifier", DEFAULT.lookTurnSpeedModifier).forGetter(Orientation::lookTurnSpeedModifier),
                        Codec.DOUBLE.optionalFieldOf("path_look_ahead_radius", DEFAULT.pathLookAheadRadiusMultiplier).forGetter(Orientation::pathLookAheadRadiusMultiplier),
                        Codec.DOUBLE.optionalFieldOf("maximum_path_look_ahead", DEFAULT.maximumPathLookAhead).forGetter(Orientation::maximumPathLookAhead),
                        Codec.FLOAT.optionalFieldOf("visual_smoothing_response", DEFAULT.visualSmoothingResponse).forGetter(Orientation::visualSmoothingResponse)
                ).apply(instance, Orientation::new));

        private DinosaurOrientationConfig toConfig(
                List<DinosaurLookBone> lookBones) {
            return new DinosaurOrientationConfig(
                    lookBones,
                    this.maxNeckYaw,
                    this.bodyTurnStartYaw,
                    this.bodyTurnStopYaw,
                    this.maxPitchUp,
                    this.maxPitchDown,
                    this.headYawSpeed,
                    this.headPitchSpeed,
                    this.neckRecenteringSpeed,
                    this.maxBodyYawChange,
                    this.steeringDegreesPerBlock,
                    this.minimumTurningDistance,
                    this.lookTurnSpeedModifier,
                    this.pathLookAheadRadiusMultiplier,
                    this.maximumPathLookAhead,
                    this.visualSmoothingResponse);
        }
    }

    public record Terrain(
            double bodyPivotHeight,
            double footContactHeight,
            double contactPatchRadius,
            double sampleAbove,
            double sampleBelow,
            double maxBodyVerticalCorrection,
            float maxPitchDegrees,
            float maxRollDegrees,
            float slopeDeadzoneDegrees) {
        public static final Terrain DEFAULT = new Terrain(
                14.0 / 16.0, 0.0, 2.0 / 16.0,
                1.25, 0.75, 0.75,
                35, 15, 0.5F);
        public static final Codec<Terrain> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.DOUBLE.optionalFieldOf("body_pivot_height", DEFAULT.bodyPivotHeight).forGetter(Terrain::bodyPivotHeight),
                        Codec.DOUBLE.optionalFieldOf("foot_contact_height", DEFAULT.footContactHeight).forGetter(Terrain::footContactHeight),
                        Codec.DOUBLE.optionalFieldOf("contact_patch_radius", DEFAULT.contactPatchRadius).forGetter(Terrain::contactPatchRadius),
                        Codec.DOUBLE.optionalFieldOf("sample_above", DEFAULT.sampleAbove).forGetter(Terrain::sampleAbove),
                        Codec.DOUBLE.optionalFieldOf("sample_below", DEFAULT.sampleBelow).forGetter(Terrain::sampleBelow),
                        Codec.DOUBLE.optionalFieldOf("max_body_vertical_correction", DEFAULT.maxBodyVerticalCorrection).forGetter(Terrain::maxBodyVerticalCorrection),
                        Codec.FLOAT.optionalFieldOf("max_pitch", DEFAULT.maxPitchDegrees).forGetter(Terrain::maxPitchDegrees),
                        Codec.FLOAT.optionalFieldOf("max_roll", DEFAULT.maxRollDegrees).forGetter(Terrain::maxRollDegrees),
                        Codec.FLOAT.optionalFieldOf("slope_deadzone", DEFAULT.slopeDeadzoneDegrees).forGetter(Terrain::slopeDeadzoneDegrees)
                ).apply(instance, Terrain::new));
    }

    public record LegMotion(
            float minReachFraction,
            float maxReachFraction,
            float bodyTiltStartFraction,
            float bodyTiltSlopeShare,
            float maxHybridPitchDegrees,
            float maxHybridRollDegrees,
            float maxFootPitchDegrees,
            float maxFootRollDegrees,
            float smoothingResponse) {
        public static final LegMotion DEFAULT = new LegMotion(
                0.35F, 0.985F, 0.60F, 0.35F,
                10, 7, 25, 25, 9);
        public static final Codec<LegMotion> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("min_reach", DEFAULT.minReachFraction).forGetter(LegMotion::minReachFraction),
                        Codec.FLOAT.optionalFieldOf("max_reach", DEFAULT.maxReachFraction).forGetter(LegMotion::maxReachFraction),
                        Codec.FLOAT.optionalFieldOf("body_tilt_start", DEFAULT.bodyTiltStartFraction).forGetter(LegMotion::bodyTiltStartFraction),
                        Codec.FLOAT.optionalFieldOf("body_tilt_slope_share", DEFAULT.bodyTiltSlopeShare).forGetter(LegMotion::bodyTiltSlopeShare),
                        Codec.FLOAT.optionalFieldOf("max_hybrid_pitch", DEFAULT.maxHybridPitchDegrees).forGetter(LegMotion::maxHybridPitchDegrees),
                        Codec.FLOAT.optionalFieldOf("max_hybrid_roll", DEFAULT.maxHybridRollDegrees).forGetter(LegMotion::maxHybridRollDegrees),
                        Codec.FLOAT.optionalFieldOf("max_foot_pitch", DEFAULT.maxFootPitchDegrees).forGetter(LegMotion::maxFootPitchDegrees),
                        Codec.FLOAT.optionalFieldOf("max_foot_roll", DEFAULT.maxFootRollDegrees).forGetter(LegMotion::maxFootRollDegrees),
                        Codec.FLOAT.optionalFieldOf("smoothing_response", DEFAULT.smoothingResponse).forGetter(LegMotion::smoothingResponse)
                ).apply(instance, LegMotion::new));
    }
}
