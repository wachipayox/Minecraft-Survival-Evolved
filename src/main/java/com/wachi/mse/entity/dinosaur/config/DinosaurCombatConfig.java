package com.wachi.mse.entity.dinosaur.config;

import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Data-driven attacks for one dinosaur species.
 */
public record DinosaurCombatConfig(List<Attack> attacks) {
    public static final DinosaurCombatConfig PROTOTYPE =
            new DinosaurCombatConfig(List.of(new Attack(
                    "bite",
                    "animation.prototype_dinosaur.bite",
                    16,
                    7,
                    8,
                    24,
                    1.0F,
                    0.35F,
                    HitMode.PRIMARY_TARGET,
                    List.of(new AttackVolume(
                            "head",
                            new Vec3(0.0, 22.0 / 16.0, -33.0 / 16.0),
                            new Vec3(5.0 / 16.0, 4.5 / 16.0, 6.0 / 16.0))),
                    List.of(
                            frame(0),
                            frame(
                                    3,
                                    rotation("neck_1", 10, 0, 0),
                                    rotation("neck_2", 15, 0, 0),
                                    rotation("head", 8, 0, 0),
                                    rotation("jaw", -38, 0, 0)),
                            frame(
                                    6,
                                    rotation("neck_1", -12, 0, 0),
                                    rotation("neck_2", -18, 0, 0),
                                    rotation("head", -12, 0, 0),
                                    rotation("jaw", -38, 0, 0)),
                            frame(
                                    7,
                                    rotation("neck_1", -12, 0, 0),
                                    rotation("neck_2", -18, 0, 0),
                                    rotation("head", -12, 0, 0)),
                            frame(
                                    10,
                                    rotation("neck_1", -3, 0, 0),
                                    rotation("neck_2", -5, 0, 0),
                                    rotation("head", -3, 0, 0)),
                            frame(16)))));

    public DinosaurCombatConfig {
        attacks = List.copyOf(attacks);
        Set<String> ids = new HashSet<>();
        for (Attack attack : attacks) {
            if (!ids.add(attack.id())) {
                throw new IllegalArgumentException(
                        "Duplicate dinosaur attack " + attack.id());
            }
        }
        if (attacks.isEmpty()) {
            throw new IllegalArgumentException(
                    "A combat-capable dinosaur needs an attack");
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
        AREA
    }

    @SafeVarargs
    private static Keyframe frame(
            int tick,
            Map.Entry<String, DinosaurBoneRotation>... entries) {
        return new Keyframe(tick, Map.ofEntries(entries));
    }

    private static Map.Entry<String, DinosaurBoneRotation> rotation(
            String bone,
            float xDegrees,
            float yDegrees,
            float zDegrees) {
        return Map.entry(
                bone,
                new DinosaurBoneRotation(
                        (float) Math.toRadians(xDegrees),
                        (float) Math.toRadians(yDegrees),
                        (float) Math.toRadians(zDegrees)));
    }
}
