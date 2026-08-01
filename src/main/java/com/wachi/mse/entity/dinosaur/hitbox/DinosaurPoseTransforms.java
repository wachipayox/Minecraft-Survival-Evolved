package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLookBone;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurRotationMath;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurRotationMath.Quaternion;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
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

    /**
     * Tests the thin volume occupied by an entity's feet against the actual
     * oriented bone boxes. Unlike their broad-phase AABBs, this does not fill
     * the empty corners created when a long model cube rotates.
     */
    public static boolean hitboxPartIntersectsFeet(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.HitboxPart part,
            AABB entityBounds,
            double belowFeetTolerance,
            double aboveFeetTolerance) {
        AABB feet = new AABB(
                entityBounds.minX,
                entityBounds.minY - belowFeetTolerance,
                entityBounds.minZ,
                entityBounds.maxX,
                entityBounds.minY + aboveFeetTolerance,
                entityBounds.maxZ);
        for (DinosaurSkeletonConfig.BoneBox box : part.boxes()) {
            if (orientedBoxIntersectsAabb(
                    transformBoxCorners(
                            snapshot,
                            box.boneName(),
                            box.minimum(),
                            box.maximum()),
                    feet)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hitboxBoxIntersectsAabb(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.BoneBox box,
            AABB bounds) {
        return orientedBoxIntersectsAabb(
                transformBoxCorners(
                        snapshot,
                        box.boneName(),
                        box.minimum(),
                        box.maximum()),
                bounds);
    }

    public static AABB hitboxBoxBounds(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.BoneBox box) {
        return transformBox(
                snapshot,
                box.boneName(),
                box.minimum(),
                box.maximum());
    }

    /**
     * Carries a world-space contact point with the same animated bone box
     * between two poses. Coordinates are recovered inside the previous
     * oriented box and reapplied to the current one, so translation, body
     * turning and bone animation all contribute to platform movement.
     */
    public static Vec3 transportPoint(
            PoseSnapshot previous,
            PoseSnapshot current,
            DinosaurSkeletonConfig.BoneBox box,
            Vec3 worldPoint) {
        OrientedBoxFrame previousFrame = orientedBoxFrame(previous, box);
        OrientedBoxFrame currentFrame = orientedBoxFrame(current, box);
        return currentFrame.toWorld(previousFrame.toLocal(worldPoint));
    }

    /**
     * Finds a real upward-facing contact beneath an entity instead of
     * mistaking an OBB side wall for a platform. Several footprint samples
     * allow an entity to stand near an edge even when its center is outside
     * the supporting box.
     */
    public static Vec3 hitboxBoxSupportPoint(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.BoneBox box,
            AABB entityBounds,
            double belowFeetTolerance,
            double aboveFeetTolerance) {
        Vec3[] corners = transformBoxCorners(
                snapshot,
                box.boneName(),
                box.minimum(),
                box.maximum());
        double inset = 1.0E-4;
        double minX = entityBounds.minX + inset;
        double maxX = entityBounds.maxX - inset;
        double minZ = entityBounds.minZ + inset;
        double maxZ = entityBounds.maxZ - inset;
        double centerX = (minX + maxX) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double centerSupportY = verticalLineTop(
                corners,
                centerX,
                centerZ);
        if (isSupportHeight(
                centerSupportY,
                entityBounds.minY,
                belowFeetTolerance,
                aboveFeetTolerance)) {
            return new Vec3(
                    centerX,
                    centerSupportY,
                    centerZ);
        }

        /*
         * The center is deliberately not mixed into the fallback search.
         * On a tilted surface, selecting the highest of center and corners
         * changes the local anchor as the box rotates and accumulates fake
         * horizontal platform movement. Corners exist only so an entity can
         * remain supported when its center has genuinely crossed an edge.
         */
        double[] xs = {minX, maxX};
        double[] zs = {minZ, maxZ};
        Vec3 best = null;
        double bestHorizontalDistanceSquared =
                Double.POSITIVE_INFINITY;
        for (double x : xs) {
            for (double z : zs) {
                double supportY = verticalLineTop(corners, x, z);
                if (!isSupportHeight(
                        supportY,
                        entityBounds.minY,
                        belowFeetTolerance,
                        aboveFeetTolerance)) {
                    continue;
                }
                double horizontalDistanceSquared =
                        square(x - centerX)
                                + square(z - centerZ);
                if (best == null
                        || horizontalDistanceSquared
                                < bestHorizontalDistanceSquared) {
                    best = new Vec3(x, supportY, z);
                    bestHorizontalDistanceSquared =
                            horizontalDistanceSquared;
                }
            }
        }
        return best;
    }

    /**
     * Returns the actual oriented top surface beneath an entity footprint.
     * This deliberately does not use the transformed box AABB: its maxY is
     * merely the highest rotated corner and can be far above the local
     * surface, especially on large dinosaurs.
     */
    public static Vec3 hitboxBoxTopSurfacePoint(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.BoneBox box,
            AABB entityBounds) {
        Vec3[] corners = transformBoxCorners(
                snapshot,
                box.boneName(),
                box.minimum(),
                box.maximum());
        double inset = 1.0E-4;
        double minX = entityBounds.minX + inset;
        double maxX = entityBounds.maxX - inset;
        double minZ = entityBounds.minZ + inset;
        double maxZ = entityBounds.maxZ - inset;
        double centerX = (minX + maxX) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double centerY = verticalLineTop(
                corners,
                centerX,
                centerZ);
        if (Double.isFinite(centerY)) {
            return new Vec3(centerX, centerY, centerZ);
        }

        Vec3 highest = null;
        double[] xs = {minX, maxX};
        double[] zs = {minZ, maxZ};
        for (double x : xs) {
            for (double z : zs) {
                double surfaceY = verticalLineTop(corners, x, z);
                if (Double.isFinite(surfaceY)
                        && (highest == null
                                || surfaceY > highest.y)) {
                    highest = new Vec3(x, surfaceY, z);
                }
            }
        }
        return highest;
    }

    private static boolean isSupportHeight(
            double supportY,
            double feetY,
            double belowFeetTolerance,
            double aboveFeetTolerance) {
        return Double.isFinite(supportY)
                && supportY >= feetY - belowFeetTolerance
                && supportY <= feetY + aboveFeetTolerance;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double verticalLineTop(
            Vec3[] corners,
            double x,
            double z) {
        Vec3 origin = corners[0];
        Vec3 pointAtZeroY = new Vec3(x, 0.0, z);
        Vec3[] axes = {
            corners[1].subtract(origin),
            corners[2].subtract(origin),
            corners[4].subtract(origin)
        };
        double minimumY = Double.NEGATIVE_INFINITY;
        double maximumY = Double.POSITIVE_INFINITY;
        for (Vec3 axis : axes) {
            double lengthSquared = axis.lengthSqr();
            if (lengthSquared <= 1.0E-12) {
                return Double.NaN;
            }
            double base = pointAtZeroY
                    .subtract(origin)
                    .dot(axis)
                    / lengthSquared;
            double slope = axis.y / lengthSquared;
            if (Math.abs(slope) <= 1.0E-12) {
                if (base < -1.0E-7 || base > 1.0 + 1.0E-7) {
                    return Double.NaN;
                }
                continue;
            }
            double first = -base / slope;
            double second = (1.0 - base) / slope;
            minimumY = Math.max(
                    minimumY,
                    Math.min(first, second));
            maximumY = Math.min(
                    maximumY,
                    Math.max(first, second));
            if (minimumY > maximumY + 1.0E-7) {
                return Double.NaN;
            }
        }
        return maximumY;
    }

    private static OrientedBoxFrame orientedBoxFrame(
            PoseSnapshot snapshot,
            DinosaurSkeletonConfig.BoneBox box) {
        Vec3[] corners = transformBoxCorners(
                snapshot,
                box.boneName(),
                box.minimum(),
                box.maximum());
        return new OrientedBoxFrame(
                corners[0],
                corners[1].subtract(corners[0]),
                corners[2].subtract(corners[0]),
                corners[4].subtract(corners[0]));
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
            Quaternion parentWorld = DinosaurRotationMath.parentWorldRotation(
                    config.skeleton(),
                    rig.footBone(),
                    boneName -> rotations.getOrDefault(
                            boneName,
                            DinosaurBoneRotation.ZERO));
            Quaternion authoredWorld = parentWorld;
            Quaternion terrainWorld =
                    DinosaurRotationMath.terrainOrientation(
                            legPose.footTerrainPitchRadians(),
                            legPose.footTerrainRollRadians());
            Quaternion blendedWorld = authoredWorld.slerp(
                    terrainWorld,
                    ikWeight);
            rotations.put(
                    rig.footBone(),
                    parentWorld
                            .inverse()
                            .multiply(blendedWorld)
                            .toEuler());

            double authoredClearance =
                    DinosaurRotationMath.footPivotClearance(
                            rig,
                            authoredWorld,
                            terrainWorld);
            double proceduralClearance =
                    DinosaurRotationMath.footPivotClearance(
                            rig,
                            terrainWorld,
                            terrainWorld);
            double blendedClearance =
                    DinosaurRotationMath.footPivotClearance(
                            rig,
                            blendedWorld,
                            terrainWorld);
            double residualLift = Math.max(
                    0.0,
                    blendedClearance
                            - Mth.lerp(
                                    ikWeight,
                                    authoredClearance,
                                    proceduralClearance));
            if (residualLift > 1.0E-6) {
                translations.put(
                        rig.footBone(),
                        parentWorld
                                .inverse()
                                .rotate(new Vec3(
                                        0.0,
                                        residualLift,
                                        0.0)));
            }
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
        for (Vec3 worldPoint : transformBoxCorners(
                snapshot,
                boneName,
                minimum,
                maximum)) {
            AABB pointBounds = new AABB(worldPoint, worldPoint);
            result = result == null ? pointBounds : result.minmax(pointBounds);
        }
        return result.inflate(1.0E-4);
    }

    private static Vec3[] transformBoxCorners(
            PoseSnapshot snapshot,
            String boneName,
            Vec3 minimum,
            Vec3 maximum) {
        Vec3[] corners = new Vec3[8];
        for (int corner = 0; corner < corners.length; corner++) {
            Vec3 modelPoint = new Vec3(
                    (corner & 1) == 0 ? minimum.x : maximum.x,
                    (corner & 2) == 0 ? minimum.y : maximum.y,
                    (corner & 4) == 0 ? minimum.z : maximum.z);
            corners[corner] = transformPoint(
                    snapshot,
                    boneName,
                    modelPoint);
        }
        return corners;
    }

    private static boolean orientedBoxIntersectsAabb(
            Vec3[] orientedCorners,
            AABB axisAlignedBox) {
        Vec3[] orientedAxes = {
            orientedCorners[1].subtract(orientedCorners[0]),
            orientedCorners[2].subtract(orientedCorners[0]),
            orientedCorners[4].subtract(orientedCorners[0])
        };
        Vec3[] worldAxes = {
            new Vec3(1.0, 0.0, 0.0),
            new Vec3(0.0, 1.0, 0.0),
            new Vec3(0.0, 0.0, 1.0)
        };

        for (Vec3 axis : worldAxes) {
            if (separatedOnAxis(orientedCorners, axisAlignedBox, axis)) {
                return false;
            }
        }
        for (Vec3 axis : orientedAxes) {
            if (separatedOnAxis(orientedCorners, axisAlignedBox, axis)) {
                return false;
            }
        }
        for (Vec3 worldAxis : worldAxes) {
            for (Vec3 orientedAxis : orientedAxes) {
                Vec3 cross = worldAxis.cross(orientedAxis);
                if (separatedOnAxis(
                        orientedCorners,
                        axisAlignedBox,
                        cross)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean separatedOnAxis(
            Vec3[] orientedCorners,
            AABB axisAlignedBox,
            Vec3 axis) {
        if (axis.lengthSqr() < 1.0E-12) {
            return false;
        }

        double orientedMin = Double.POSITIVE_INFINITY;
        double orientedMax = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : orientedCorners) {
            double projection = corner.dot(axis);
            orientedMin = Math.min(orientedMin, projection);
            orientedMax = Math.max(orientedMax, projection);
        }

        Vec3 center = axisAlignedBox.getCenter();
        double centerProjection = center.dot(axis);
        double radius =
                axisAlignedBox.getXsize() * 0.5 * Math.abs(axis.x)
                        + axisAlignedBox.getYsize()
                                * 0.5
                                * Math.abs(axis.y)
                        + axisAlignedBox.getZsize()
                                * 0.5
                                * Math.abs(axis.z);
        double axisAlignedMin = centerProjection - radius;
        double axisAlignedMax = centerProjection + radius;
        return orientedMax < axisAlignedMin - 1.0E-7
                || axisAlignedMax < orientedMin - 1.0E-7;
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

    /**
     * Affine frame of one model cube after the complete bone hierarchy and
     * dinosaur body rotation have been applied. The native AABB never enters
     * this conversion; it remains broad-phase data only.
     */
    private record OrientedBoxFrame(
            Vec3 origin,
            Vec3 xAxis,
            Vec3 yAxis,
            Vec3 zAxis) {
        Vec3 toLocal(Vec3 worldPoint) {
            Vec3 offset = worldPoint.subtract(this.origin);
            return new Vec3(
                    axisCoordinate(offset, this.xAxis),
                    axisCoordinate(offset, this.yAxis),
                    axisCoordinate(offset, this.zAxis));
        }

        Vec3 toWorld(Vec3 localPoint) {
            return this.origin
                    .add(this.xAxis.scale(localPoint.x))
                    .add(this.yAxis.scale(localPoint.y))
                    .add(this.zAxis.scale(localPoint.z));
        }

        private static double axisCoordinate(
                Vec3 offset,
                Vec3 axis) {
            double lengthSquared = axis.lengthSqr();
            if (lengthSquared <= 1.0E-12) {
                return 0.0;
            }
            return Mth.clamp(
                    offset.dot(axis) / lengthSquared,
                    0.0,
                    1.0);
        }
    }
}
