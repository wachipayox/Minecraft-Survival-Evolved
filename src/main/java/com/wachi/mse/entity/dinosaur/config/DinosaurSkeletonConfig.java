package com.wachi.mse.entity.dinosaur.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/**
 * Species-owned skeleton metadata used by logical hitboxes.
 *
 * <p>Coordinates are expressed in unscaled Blockbench blocks. Runtime
 * transforms apply {@link DinosaurProceduralConfig#modelScale()} once, so the
 * same profile is valid for the normal and giant prototypes.</p>
 */
public record DinosaurSkeletonConfig(
        List<Bone> bones,
        List<HitboxPart> hitboxParts) {
    public static final DinosaurSkeletonConfig PROTOTYPE = createPrototype();

    public DinosaurSkeletonConfig {
        bones = List.copyOf(bones);
        hitboxParts = List.copyOf(hitboxParts);
        Set<String> boneNames = new HashSet<>();
        for (Bone bone : bones) {
            if (!boneNames.add(bone.name())) {
                throw new IllegalArgumentException(
                        "Duplicate skeleton bone " + bone.name());
            }
            if (bone.parent() != null && !boneNames.contains(bone.parent())) {
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
        for (Bone bone : this.bones) {
            if (bone.name().equals(name)) {
                return bone;
            }
        }
        throw new IllegalArgumentException("Unknown dinosaur bone " + name);
    }

    public HitboxPart hitboxPart(String id) {
        for (HitboxPart part : this.hitboxParts) {
            if (part.id().equals(id)) {
                return part;
            }
        }
        throw new IllegalArgumentException("Unknown dinosaur hitbox part " + id);
    }

    public record Bone(
            String name,
            String parent,
            Vec3 pivot) {
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
     * One selectable multipart entity. A part may unite several small bone
     * boxes, keeping the native multipart broad phase bounded to ten entries
     * for the prototype instead of one entry per model cube.
     */
    public record HitboxPart(
            String id,
            BodyRegion region,
            float incomingDamageMultiplier,
            List<BoneBox> boxes) {
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
        LEG
    }

    private static DinosaurSkeletonConfig createPrototype() {
        double unit = 1.0 / 16.0;
        List<Bone> bones = List.of(
                bone("root", null, 0, 0, 0, unit),
                bone("body", "root", 0, 14, 0, unit),
                bone("chest", "body", 0, 14, -7, unit),
                bone("neck_1", "chest", 0, 16, -13, unit),
                bone("neck_2", "neck_1", 0, 19, -19, unit),
                bone("head", "neck_2", 0, 23, -25, unit),
                bone("jaw", "head", 0, 21, -29, unit),
                bone("pelvis", "body", 0, 14, 7, unit),
                bone("tail_1", "pelvis", 0, 15, 13, unit),
                bone("tail_2", "tail_1", 0, 15, 23, unit),
                bone("tail_3", "tail_2", 0, 15, 33, unit),
                bone("leg_front_left", "chest", 4, 12, -9, unit),
                bone("shin_front_left", "leg_front_left", 4, 6, -9, unit),
                bone("foot_front_left", "shin_front_left", 4, 1, -9, unit),
                bone("leg_front_right", "chest", -4, 12, -9, unit),
                bone("shin_front_right", "leg_front_right", -4, 6, -9, unit),
                bone("foot_front_right", "shin_front_right", -4, 1, -9, unit),
                bone("leg_back_left", "pelvis", 4, 12, 9, unit),
                bone("shin_back_left", "leg_back_left", 4, 6, 9, unit),
                bone("foot_back_left", "shin_back_left", 4, 1, 9, unit),
                bone("leg_back_right", "pelvis", -4, 12, 9, unit),
                bone("shin_back_right", "leg_back_right", -4, 6, 9, unit),
                bone("foot_back_right", "shin_back_right", -4, 1, 9, unit));

        List<HitboxPart> parts = new ArrayList<>();
        parts.add(part(
                "torso",
                BodyRegion.TORSO,
                box("body", -6, 9, -7.5, 6, 19, 7.5, unit),
                box("chest", -5.5, 10, -14, 5.5, 19, -6.5, unit),
                box("pelvis", -5.5, 10, 6.5, 5.5, 19, 14, unit)));
        parts.add(part(
                "neck_1",
                BodyRegion.NECK,
                box("neck_1", -3.5, 13, -20, 3.5, 20, -12, unit)));
        parts.add(part(
                "neck_2",
                BodyRegion.NECK,
                box("neck_2", -3, 17, -26, 3, 23, -18, unit)));
        parts.add(part(
                "head",
                BodyRegion.HEAD,
                box("head", -4, 20, -31, 4, 27, -24, unit),
                box("head", -3.5, 20, -35, 3.5, 25, -29, unit),
                box("jaw", -3, 18, -34, 3, 22, -28, unit)));
        parts.add(part(
                "tail_near",
                BodyRegion.TAIL,
                box("tail_1", -4, 12, 13, 4, 18, 24, unit)));
        parts.add(part(
                "tail_far",
                BodyRegion.TAIL,
                box("tail_2", -3, 13, 23, 3, 17, 34, unit),
                box("tail_3", -2, 14, 33, 2, 17, 45, unit)));
        parts.add(legPart("front_left", "leg_front_left", "shin_front_left",
                "foot_front_left", 2.5, 5, -11, 5.5, 13, -7, 3, 0, -10,
                5, 7, -8, 2.5, 0, -12, 5.5, 2, -7, unit));
        parts.add(legPart("front_right", "leg_front_right", "shin_front_right",
                "foot_front_right", -5.5, 5, -11, -2.5, 13, -7, -5, 0, -10,
                -3, 7, -8, -5.5, 0, -12, -2.5, 2, -7, unit));
        parts.add(legPart("back_left", "leg_back_left", "shin_back_left",
                "foot_back_left", 2.5, 5, 7, 5.5, 13, 11, 3, 0, 8,
                5, 7, 10, 2.5, 0, 6, 5.5, 2, 11, unit));
        parts.add(legPart("back_right", "leg_back_right", "shin_back_right",
                "foot_back_right", -5.5, 5, 7, -2.5, 13, 11, -5, 0, 8,
                -3, 7, 10, -5.5, 0, 6, -2.5, 2, 11, unit));
        return new DinosaurSkeletonConfig(bones, parts);
    }

    private static Bone bone(
            String name,
            String parent,
            double x,
            double y,
            double z,
            double unit) {
        return new Bone(name, parent, new Vec3(x * unit, y * unit, z * unit));
    }

    private static BoneBox box(
            String bone,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            double unit) {
        return new BoneBox(
                bone,
                new Vec3(minX * unit, minY * unit, minZ * unit),
                new Vec3(maxX * unit, maxY * unit, maxZ * unit));
    }

    private static HitboxPart part(
            String id,
            BodyRegion region,
            BoneBox... boxes) {
        return new HitboxPart(id, region, 1.0F, List.of(boxes));
    }

    private static HitboxPart legPart(
            String id,
            String upperBone,
            String lowerBone,
            String footBone,
            double ux0,
            double uy0,
            double uz0,
            double ux1,
            double uy1,
            double uz1,
            double lx0,
            double ly0,
            double lz0,
            double lx1,
            double ly1,
            double lz1,
            double fx0,
            double fy0,
            double fz0,
            double fx1,
            double fy1,
            double fz1,
            double unit) {
        return part(
                "leg_" + id,
                BodyRegion.LEG,
                box(upperBone, ux0, uy0, uz0, ux1, uy1, uz1, unit),
                box(lowerBone, lx0, ly0, lz0, lx1, ly1, lz1, unit),
                box(footBone, fx0, fy0, fz0, fx1, fy1, fz1, unit));
    }
}
