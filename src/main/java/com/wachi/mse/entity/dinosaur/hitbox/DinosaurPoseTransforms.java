package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLookBone;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Transforms profile-space geometry with the same procedural pose used by the
 * renderer. Selection parts, culling and attack volumes consequently follow
 * body balance, stance-weighted leg IK, neck look and attack animation from
 * one source.
 */
public final class DinosaurPoseTransforms {
    private DinosaurPoseTransforms() {
    }

    public static AABB hitboxPartBounds(
            DinosaurProceduralConfig config,
            DinosaurProceduralPose pose,
            DinosaurSkeletonConfig.HitboxPart part,
            DinosaurCombatConfig.Attack attack,
            float attackElapsedTicks) {
        return hitboxPartBounds(
                snapshot(config, pose, attack, attackElapsedTicks),
                part);
    }

    public static AABB hitboxPartBounds(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.HitboxPart part) {
        AABB result = null;
        for (DinosaurSkeletonConfig.BoneBox box : part.boxes()) {
            AABB transformed = transformBox(
                    snapshot,
                    box.boneName(),
                    box.minimum(),
                    box.maximum());
            result = result == null ? transformed : result.minmax(transformed);
        }
        return result;
    }

    public static List<AABB> hitboxPartBoxBounds(
            DinosaurProceduralConfig config,
            DinosaurProceduralPose pose,
            DinosaurSkeletonConfig.HitboxPart part,
            DinosaurCombatConfig.Attack attack,
            float attackElapsedTicks) {
        return hitboxPartBoxBounds(
                snapshot(config, pose, attack, attackElapsedTicks),
                part);
    }

    public static List<AABB> hitboxPartBoxBounds(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.HitboxPart part) {
        List<AABB> result = new ArrayList<>(part.boxes().size());
        for (DinosaurSkeletonConfig.BoneBox box : part.boxes()) {
            result.add(transformBox(
                    snapshot,
                    box.boneName(),
                    box.minimum(),
                    box.maximum()));
        }
        return List.copyOf(result);
    }

    public static AABB attackVolumeBounds(
            DinosaurProceduralConfig config,
            DinosaurProceduralPose pose,
            DinosaurCombatConfig.Attack attack,
            DinosaurCombatConfig.AttackVolume volume,
            float attackElapsedTicks) {
        return attackVolumeBounds(
                snapshot(config, pose, attack, attackElapsedTicks),
                volume);
    }

    public static AABB attackVolumeBounds(
            PoseSnapshot snapshot,
            DinosaurCombatConfig.AttackVolume volume) {
        Vec3 minimum = volume.center().subtract(volume.halfExtents());
        Vec3 maximum = volume.center().add(volume.halfExtents());
        return transformBox(
                snapshot,
                volume.boneName(),
                minimum,
                maximum);
    }

    public static PoseSnapshot snapshot(
            DinosaurProceduralConfig config,
            DinosaurProceduralPose pose,
            DinosaurCombatConfig.Attack attack,
            float attackElapsedTicks) {
        Map<String, DinosaurBoneRotation> rotations = new HashMap<>();
        Map<String, Vec3> translations = new HashMap<>();
        rotations.put(
                config.bodyBone(),
                new DinosaurBoneRotation(
                        pose.pitchRadians(),
                        0.0F,
                        pose.rollRadians()));

        float lookPitch =
                pose.orientation().pitchRadians() - pose.pitchRadians();
        for (DinosaurLookBone lookBone : config.orientation().lookBones()) {
            addRotation(
                    rotations,
                    lookBone.boneName(),
                    new DinosaurBoneRotation(
                            lookPitch * lookBone.pitchWeight(),
                            -pose.orientation().yawRadians()
                                    * lookBone.yawWeight(),
                            0.0F));
        }

        for (DinosaurLegPose legPose : pose.legs()) {
            DinosaurLegRig rig = config.leg(legPose.legId());
            float ikWeight = legPose.forcedMaximumExtension()
                    ? 1.0F
                    : pose.gait().supportWeight(legPose.legId());
            addRotation(
                    rotations,
                    rig.upperBone(),
                    new DinosaurBoneRotation(
                            legPose.hipRotation().xRadians() * ikWeight
                                    - pose.balancePitchRadians(),
                            legPose.hipRotation().yRadians() * ikWeight,
                            legPose.hipRotation().zRadians() * ikWeight
                                    - pose.balanceRollRadians()));
            translations.put(
                    rig.upperBone(),
                    legRootCompensation(config, rig, pose));
            addRotation(
                    rotations,
                    rig.lowerBone(),
                    scaledRotation(legPose.kneeRotation(), ikWeight));
            DinosaurBoneRotation foot = legPose.footRotation();
            addRotation(
                    rotations,
                    rig.footBone(),
                    scaledRotation(new DinosaurBoneRotation(
                            foot.xRadians()
                                    + legPose.footTerrainPitchRadians(),
                            foot.yRadians(),
                            foot.zRadians()
                                    + legPose.footTerrainRollRadians()),
                            ikWeight));
        }

        if (attack != null) {
            for (Map.Entry<String, DinosaurBoneRotation> entry
                    : attack.rotationsAt(attackElapsedTicks).entrySet()) {
                addRotation(rotations, entry.getKey(), entry.getValue());
            }
        }
        Map<String, DinosaurSkeletonConfig.Bone> bonesByName =
                new HashMap<>();
        for (DinosaurSkeletonConfig.Bone bone : config.skeleton().bones()) {
            bonesByName.put(bone.name(), bone);
        }
        return new PoseSnapshot(
                config,
                pose,
                Map.copyOf(rotations),
                Map.copyOf(translations),
                Map.copyOf(bonesByName));
    }

    private static AABB transformBox(
            PoseSnapshot snapshot,
            String boneName,
            Vec3 minimum,
            Vec3 maximum) {
        AABB result = null;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 modelPoint = new Vec3(
                    (corner & 1) == 0 ? minimum.x : maximum.x,
                    (corner & 2) == 0 ? minimum.y : maximum.y,
                    (corner & 4) == 0 ? minimum.z : maximum.z);
            Vec3 worldPoint = transformPoint(
                    snapshot,
                    boneName,
                    modelPoint);
            AABB pointBounds = new AABB(worldPoint, worldPoint);
            result = result == null ? pointBounds : result.minmax(pointBounds);
        }
        return result.inflate(1.0E-4);
    }

    private static Vec3 transformPoint(
            PoseSnapshot snapshot,
            String boneName,
            Vec3 modelPoint) {
        double scale = snapshot.config().scale();
        Vec3 point = renderCoordinates(modelPoint, scale);
        String currentBone = boneName;
        while (currentBone != null) {
            DinosaurSkeletonConfig.Bone bone =
                    snapshot.bonesByName().get(currentBone);
            if (bone == null) {
                throw new IllegalArgumentException(
                        "Unknown dinosaur bone " + currentBone);
            }
            DinosaurBoneRotation rotation = snapshot.rotations()
                    .getOrDefault(currentBone, DinosaurBoneRotation.ZERO);
            if (rotation != DinosaurBoneRotation.ZERO) {
                Vec3 pivot = renderCoordinates(bone.pivot(), scale);
                point = rotateAround(point, pivot, rotation);
            }
            point = point.add(snapshot.translations()
                    .getOrDefault(currentBone, Vec3.ZERO));
            currentBone = bone.parent();
        }

        DinosaurProceduralPose pose = snapshot.pose();
        double modelYaw = Math.toRadians(180.0F - pose.bodyYawDegrees());
        double sinYaw = Math.sin(modelYaw);
        double cosYaw = Math.cos(modelYaw);
        return new Vec3(
                pose.origin().x + cosYaw * point.x + sinYaw * point.z,
                pose.origin().y
                        + pose.bodyTranslationYBlocks()
                        + point.y,
                pose.origin().z - sinYaw * point.x + cosYaw * point.z);
    }

    private static Vec3 renderCoordinates(Vec3 point, double scale) {
        return new Vec3(
                -point.x * scale,
                point.y * scale,
                point.z * scale);
    }

    private static Vec3 rotateAround(
            Vec3 point,
            Vec3 pivot,
            DinosaurBoneRotation rotation) {
        Vec3 offset = point.subtract(pivot);
        offset = rotateX(offset, rotation.xRadians());
        offset = rotateY(offset, rotation.yRadians());
        offset = rotateZ(offset, rotation.zRadians());
        return pivot.add(offset);
    }

    private static Vec3 rotateX(Vec3 point, float angle) {
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(
                point.x,
                point.y * cosine - point.z * sine,
                point.y * sine + point.z * cosine);
    }

    private static Vec3 rotateY(Vec3 point, float angle) {
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(
                point.x * cosine + point.z * sine,
                point.y,
                -point.x * sine + point.z * cosine);
    }

    private static Vec3 rotateZ(Vec3 point, float angle) {
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(
                point.x * cosine - point.y * sine,
                point.x * sine + point.y * cosine,
                point.z);
    }

    private static Vec3 legRootCompensation(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurProceduralPose pose) {
        if (Math.abs(pose.balancePitchRadians()) < 1.0E-6F
                && Math.abs(pose.balanceRollRadians()) < 1.0E-6F) {
            return Vec3.ZERO;
        }
        Vec3 hipFromBodyPivot = new Vec3(
                rig.renderedModelXOffset(),
                rig.hipHeight() - config.bodyPivotHeight(),
                rig.modelZOffset());
        Vec3 structurallyTilted = rotateZ(
                rotateX(
                        hipFromBodyPivot,
                        pose.structuralPitchRadians()),
                pose.structuralRollRadians());
        Vec3 totalLocal = rotateX(
                rotateZ(
                        structurallyTilted,
                        -pose.rollRadians()),
                -pose.pitchRadians());
        return totalLocal.subtract(hipFromBodyPivot);
    }

    private static void addRotation(
            Map<String, DinosaurBoneRotation> rotations,
            String bone,
            DinosaurBoneRotation addition) {
        DinosaurBoneRotation current = rotations.getOrDefault(
                bone,
                DinosaurBoneRotation.ZERO);
        rotations.put(
                bone,
                new DinosaurBoneRotation(
                        current.xRadians() + addition.xRadians(),
                        current.yRadians() + addition.yRadians(),
                        current.zRadians() + addition.zRadians()));
    }

    private static DinosaurBoneRotation scaledRotation(
            DinosaurBoneRotation rotation,
            float weight) {
        return new DinosaurBoneRotation(
                rotation.xRadians() * weight,
                rotation.yRadians() * weight,
                rotation.zRadians() * weight);
    }

    public record PoseSnapshot(
            DinosaurProceduralConfig config,
            DinosaurProceduralPose pose,
            Map<String, DinosaurBoneRotation> rotations,
            Map<String, Vec3> translations,
            Map<String, DinosaurSkeletonConfig.Bone> bonesByName) {
    }
}
