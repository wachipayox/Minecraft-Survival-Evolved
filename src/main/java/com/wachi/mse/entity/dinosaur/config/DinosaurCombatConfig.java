package com.wachi.mse.entity.dinosaur.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Data-driven attacks for one dinosaur species.
 */
public record DinosaurCombatConfig(List<Attack> attacks) {
    public static final Codec<DinosaurCombatConfig> CODEC =
            Attack.CODEC.listOf()
                    .optionalFieldOf("attacks", List.of())
                    .codec()
                    .xmap(DinosaurCombatConfig::new, DinosaurCombatConfig::attacks);
    public DinosaurCombatConfig {
        attacks = List.copyOf(attacks);
        Set<String> ids = new HashSet<>();
        for (Attack attack : attacks) {
            if (!ids.add(attack.id())) {
                throw new IllegalArgumentException(
                        "Duplicate dinosaur attack " + attack.id());
            }
        }
    }

    public Attack attack(int syncedIndex) {
        int listIndex = syncedIndex - 1;
        return listIndex >= 0 && listIndex < this.attacks.size()
                ? this.attacks.get(listIndex)
                : null;
    }

    public int syncedIndex(Attack attack) {
        int index = this.attacks.indexOf(attack);
        return index < 0 ? 0 : index + 1;
    }

    public record Attack(
            String id,
            String animationName,
            int durationTicks,
            int activeStartTick,
            int activeEndTick,
            int cooldownTicks,
            float damageMultiplier,
            float knockback,
            HitMode hitMode,
            List<AttackVolume> volumes,
            List<Keyframe> keyframes) {
        public static final Codec<Attack> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("id").forGetter(Attack::id),
                        Codec.STRING.fieldOf("animation")
                                .forGetter(Attack::animationName),
                        Codec.INT.fieldOf("duration").forGetter(Attack::durationTicks),
                        Codec.INT.fieldOf("active_start")
                                .forGetter(Attack::activeStartTick),
                        Codec.INT.fieldOf("active_end")
                                .forGetter(Attack::activeEndTick),
                        Codec.INT.fieldOf("cooldown").forGetter(Attack::cooldownTicks),
                        Codec.FLOAT.optionalFieldOf("damage_multiplier", 1.0F)
                                .forGetter(Attack::damageMultiplier),
                        Codec.FLOAT.optionalFieldOf("knockback", 0.0F)
                                .forGetter(Attack::knockback),
                        HitMode.CODEC.optionalFieldOf(
                                        "hit_mode",
                                        HitMode.PRIMARY_TARGET)
                                .forGetter(Attack::hitMode),
                        AttackVolume.CODEC.listOf().fieldOf("volumes")
                                .forGetter(Attack::volumes),
                        Keyframe.CODEC.listOf().fieldOf("keyframes")
                                .forGetter(Attack::keyframes)
                ).apply(instance, Attack::new));

        public Attack {
            volumes = List.copyOf(volumes);
            keyframes = List.copyOf(keyframes);
            if (id == null
                    || id.isBlank()
                    || animationName == null
                    || animationName.isBlank()
                    || durationTicks < 1
                    || activeStartTick < 0
                    || activeEndTick <= activeStartTick
                    || activeEndTick > durationTicks
                    || cooldownTicks < durationTicks
                    || !Float.isFinite(damageMultiplier)
                    || damageMultiplier <= 0.0F
                    || !Float.isFinite(knockback)
                    || knockback < 0.0F
                    || hitMode == null
                    || volumes.isEmpty()
                    || keyframes.isEmpty()
                    || keyframes.getFirst().tick() != 0
                    || keyframes.getLast().tick() != durationTicks) {
                throw new IllegalArgumentException(
                        "Dinosaur attack configuration is invalid");
            }
            int previousTick = -1;
            for (Keyframe keyframe : keyframes) {
                if (keyframe.tick() <= previousTick) {
                    throw new IllegalArgumentException(
                            "Dinosaur attack keyframes must be ordered");
                }
                previousTick = keyframe.tick();
            }
        }

        public boolean activeAt(int elapsedTicks) {
            return elapsedTicks >= this.activeStartTick
                    && elapsedTicks < this.activeEndTick;
        }

        public Map<String, DinosaurBoneRotation> rotationsAt(float elapsedTicks) {
            Keyframe before = this.keyframes.getFirst();
            Keyframe after = this.keyframes.getLast();
            for (int index = 1; index < this.keyframes.size(); index++) {
                Keyframe candidate = this.keyframes.get(index);
                if (candidate.tick() >= elapsedTicks) {
                    after = candidate;
                    before = this.keyframes.get(index - 1);
                    break;
                }
            }
            if (before.tick() == after.tick()) {
                return before.rotations();
            }
            float alpha = Mth.clamp(
                    (elapsedTicks - before.tick())
                            / (after.tick() - before.tick()),
                    0.0F,
                    1.0F);
            java.util.HashMap<String, DinosaurBoneRotation> result =
                    new java.util.HashMap<>();
            Set<String> bones = new HashSet<>(before.rotations().keySet());
            bones.addAll(after.rotations().keySet());
            for (String bone : bones) {
                DinosaurBoneRotation start = before.rotations()
                        .getOrDefault(bone, DinosaurBoneRotation.ZERO);
                DinosaurBoneRotation end = after.rotations()
                        .getOrDefault(bone, DinosaurBoneRotation.ZERO);
                result.put(bone, new DinosaurBoneRotation(
                        Mth.lerp(alpha, start.xRadians(), end.xRadians()),
                        Mth.lerp(alpha, start.yRadians(), end.yRadians()),
                        Mth.lerp(alpha, start.zRadians(), end.zRadians())));
            }
            return Map.copyOf(result);
        }
    }

    public record AttackVolume(
            String boneName,
            Vec3 center,
            Vec3 halfExtents) {
        public static final Codec<AttackVolume> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("bone")
                                .forGetter(AttackVolume::boneName),
                        Vec3.CODEC.fieldOf("center")
                                .forGetter(AttackVolume::center),
                        Vec3.CODEC.fieldOf("half_extents")
                                .forGetter(AttackVolume::halfExtents)
                ).apply(instance, AttackVolume::new));

        public AttackVolume {
            if (boneName == null
                    || boneName.isBlank()
                    || center == null
                    || halfExtents == null
                    || halfExtents.x <= 0.0
                    || halfExtents.y <= 0.0
                    || halfExtents.z <= 0.0) {
                throw new IllegalArgumentException(
                        "Dinosaur attack volume is invalid");
            }
        }
    }

    public record Keyframe(
            int tick,
            Map<String, DinosaurBoneRotation> rotations) {
        private static final Codec<DinosaurBoneRotation> ROTATION_DEGREES_CODEC =
                Vec3.CODEC.xmap(
                        value -> new DinosaurBoneRotation(
                                (float) Math.toRadians(value.x),
                                (float) Math.toRadians(value.y),
                                (float) Math.toRadians(value.z)),
                        value -> new Vec3(
                                Math.toDegrees(value.xRadians()),
                                Math.toDegrees(value.yRadians()),
                                Math.toDegrees(value.zRadians())));
        public static final Codec<Keyframe> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("tick").forGetter(Keyframe::tick),
                        Codec.unboundedMap(Codec.STRING, ROTATION_DEGREES_CODEC)
                                .optionalFieldOf("rotations", Map.of())
                                .forGetter(Keyframe::rotations)
                ).apply(instance, Keyframe::new));

        public Keyframe {
            rotations = Map.copyOf(rotations);
            if (tick < 0) {
                throw new IllegalArgumentException(
                        "Attack keyframe tick cannot be negative");
            }
        }
    }

    public enum HitMode {
        PRIMARY_TARGET,
        AREA;

        public static final Codec<HitMode> CODEC = Codec.STRING.xmap(
                name -> valueOf(name.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

}
