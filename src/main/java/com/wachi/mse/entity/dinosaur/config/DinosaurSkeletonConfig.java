package com.wachi.mse.entity.dinosaur.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/**
 * Datapack skeleton metadata used by logical hitboxes.
 *
 * <p>Coordinates are expressed in unscaled Blockbench-space blocks. Runtime
 * transforms apply the current entity scale once.</p>
 */
public record DinosaurSkeletonConfig(
        List<Bone> bones,
        List<HitboxPart> hitboxParts) {
    public static final Codec<DinosaurSkeletonConfig> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Bone.CODEC.listOf().fieldOf("bones")
                            .forGetter(DinosaurSkeletonConfig::bones),
                    HitboxPart.CODEC.listOf().fieldOf("hitboxes")
                            .forGetter(DinosaurSkeletonConfig::hitboxParts)
            ).apply(instance, DinosaurSkeletonConfig::new));

    public DinosaurSkeletonConfig {
        bones = List.copyOf(bones);
        hitboxParts = List.copyOf(hitboxParts);
        Set<String> boneNames = new HashSet<>();
        for (Bone bone : bones) {
            if (!boneNames.add(bone.name())) {
                throw new IllegalArgumentException(
                        "Duplicate skeleton bone " + bone.name());
            }
            if (bone.parent() != null
                    && !boneNames.contains(bone.parent())) {
                throw new IllegalArgumentException(
                        "Skeleton parent must precede child " + bone.name());
            }
        }

        Set<String> partIds = new HashSet<>();
        for (HitboxPart part : hitboxParts) {
            if (!partIds.add(part.id())) {
                throw new IllegalArgumentException(
                        "Duplicate dinosaur hitbox part " + part.id());
            }
            for (BoneBox box : part.boxes()) {
                if (!boneNames.contains(box.boneName())) {
                    throw new IllegalArgumentException(
                            "Hitbox references unknown bone " + box.boneName());
                }
            }
        }
        if (bones.isEmpty() || hitboxParts.isEmpty()) {
            throw new IllegalArgumentException(
                    "A dinosaur skeleton needs bones and hitbox parts");
        }
    }

    public Bone bone(String name) {
        return this.bones.stream()
                .filter(bone -> bone.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dinosaur bone " + name));
    }

    public HitboxPart hitboxPart(String id) {
        return this.hitboxParts.stream()
                .filter(part -> part.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dinosaur hitbox part " + id));
    }

    public record Bone(String name, String parent, Vec3 pivot) {
        public static final Codec<Bone> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("name").forGetter(Bone::name),
                        Codec.STRING.optionalFieldOf("parent", "")
                                .forGetter(bone -> bone.parent() == null
                                        ? ""
                                        : bone.parent()),
                        Vec3.CODEC.fieldOf("pivot").forGetter(Bone::pivot)
                ).apply(instance, (name, parent, pivot) ->
                        new Bone(
                                name,
                                parent.isBlank() ? null : parent,
                                pivot)));

        public Bone {
            if (name == null || name.isBlank() || pivot == null) {
                throw new IllegalArgumentException(
                        "Skeleton bone name and pivot are required");
            }
        }
    }

    public record BoneBox(
            String boneName,
            Vec3 minimum,
            Vec3 maximum) {
        public static final Codec<BoneBox> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("bone")
                                .forGetter(BoneBox::boneName),
                        Vec3.CODEC.fieldOf("min")
                                .forGetter(BoneBox::minimum),
                        Vec3.CODEC.fieldOf("max")
                                .forGetter(BoneBox::maximum)
                ).apply(instance, BoneBox::new));

        public BoneBox {
            if (boneName == null
                    || boneName.isBlank()
                    || minimum == null
                    || maximum == null
                    || maximum.x <= minimum.x
                    || maximum.y <= minimum.y
                    || maximum.z <= minimum.z) {
                throw new IllegalArgumentException(
                        "Dinosaur bone box is invalid");
            }
        }
    }

    /**
     * One selectable multipart entity. It may group several small bone boxes
     * to keep the broad-phase entity count bounded.
     */
    public record HitboxPart(
            String id,
            BodyRegion region,
            float incomingDamageMultiplier,
            List<BoneBox> boxes) {
        public static final Codec<HitboxPart> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("id").forGetter(HitboxPart::id),
                        BodyRegion.CODEC.optionalFieldOf(
                                        "region",
                                        BodyRegion.TORSO)
                                .forGetter(HitboxPart::region),
                        Codec.FLOAT.optionalFieldOf(
                                        "damage_multiplier",
                                        1.0F)
                                .forGetter(HitboxPart::incomingDamageMultiplier),
                        BoneBox.CODEC.listOf().fieldOf("boxes")
                                .forGetter(HitboxPart::boxes)
                ).apply(instance, HitboxPart::new));

        public HitboxPart {
            boxes = List.copyOf(boxes);
            if (id == null
                    || id.isBlank()
                    || region == null
                    || !Float.isFinite(incomingDamageMultiplier)
                    || incomingDamageMultiplier <= 0.0F
                    || boxes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Dinosaur hitbox part is invalid");
            }
        }
    }

    public enum BodyRegion {
        TORSO,
        NECK,
        HEAD,
        TAIL,
        LEG;

        public static final Codec<BodyRegion> CODEC = Codec.STRING.xmap(
                name -> valueOf(name.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }
}
