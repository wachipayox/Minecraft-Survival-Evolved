package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared rotation operations used by both logical pose transforms and the
 * GeckoLib renderer.
 *
 * <p>GeckoLib composes animated bone rotations as Z, then Y, then X. Keeping
 * the same quaternion convention here avoids decomposing a partially blended
 * hierarchy into independent Euler angles.</p>
 */
public final class DinosaurRotationMath {
    private static final double EPSILON = 1.0E-9;

    private DinosaurRotationMath() {
    }

    public static Quaternion fromEuler(DinosaurBoneRotation rotation) {
        Quaternion x = new Quaternion(
                Math.sin(rotation.xRadians() * 0.5),
                0.0,
                0.0,
                Math.cos(rotation.xRadians() * 0.5));
        Quaternion y = new Quaternion(
                0.0,
                Math.sin(rotation.yRadians() * 0.5),
                0.0,
                Math.cos(rotation.yRadians() * 0.5));
        Quaternion z = new Quaternion(
                0.0,
                0.0,
                Math.sin(rotation.zRadians() * 0.5),
                Math.cos(rotation.zRadians() * 0.5));
        return z.multiply(y).multiply(x);
    }

    public static Quaternion terrainOrientation(
            float pitchRadians,
            float rollRadians) {
        return fromEuler(new DinosaurBoneRotation(
                pitchRadians,
                0.0F,
                rollRadians));
    }

    /**
     * Resolves the complete rotation of a bone's parent in model-root space.
     */
    public static Quaternion parentWorldRotation(
            DinosaurSkeletonConfig skeleton,
            String boneName,
            Function<String, DinosaurBoneRotation> localRotation) {
        String parent = skeleton.bone(boneName).parent();
        return parent == null
                ? Quaternion.IDENTITY
                : worldRotation(skeleton, parent, localRotation);
    }

    private static Quaternion worldRotation(
            DinosaurSkeletonConfig skeleton,
            String boneName,
            Function<String, DinosaurBoneRotation> localRotation) {
        DinosaurSkeletonConfig.Bone bone = skeleton.bone(boneName);
        Quaternion parent = bone.parent() == null
                ? Quaternion.IDENTITY
                : worldRotation(skeleton, bone.parent(), localRotation);
        DinosaurBoneRotation rotation = localRotation.apply(boneName);
        return rotation == null
                ? parent
                : parent.multiply(fromEuler(rotation));
    }

    /**
     * Required world-up distance between the foot pivot and an oriented
     * support plane so every corner of the configured foot box remains on or
     * above it.
     */
    public static double footPivotClearance(
            DinosaurLegRig rig,
            Quaternion footWorldRotation,
            Quaternion supportPlaneRotation) {
        Vec3 planeNormal = supportPlaneRotation.rotate(new Vec3(0.0, 1.0, 0.0));
        if (planeNormal.y <= EPSILON) {
            return rig.neutralFootPivotClearance();
        }

        Vec3 minimum = rig.footLocalMinimum();
        Vec3 maximum = rig.footLocalMaximum();
        double clearance = 0.0;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 localCorner = new Vec3(
                    (corner & 1) == 0 ? minimum.x : maximum.x,
                    (corner & 2) == 0 ? minimum.y : maximum.y,
                    (corner & 4) == 0 ? minimum.z : maximum.z);
            Vec3 rotatedCorner = footWorldRotation.rotate(localCorner);
            clearance = Math.max(
                    clearance,
                    -planeNormal.dot(rotatedCorner) / planeNormal.y);
        }
        return clearance;
    }

    public static double terrainFootPivotClearance(
            DinosaurLegRig rig,
            float pitchRadians,
            float rollRadians) {
        Quaternion terrain = terrainOrientation(pitchRadians, rollRadians);
        return footPivotClearance(rig, terrain, terrain);
    }

    /**
     * Projects the sampled ground height from the geometric center of an
     * asymmetric foot to its bone pivot on the same support plane.
     */
    public static double terrainHeightAtFootPivot(
            DinosaurLegRig rig,
            double sampledCenterHeight,
            float pitchRadians,
            float rollRadians) {
        Quaternion terrain = terrainOrientation(pitchRadians, rollRadians);
        Vec3 normal = terrain.rotate(new Vec3(0.0, 1.0, 0.0));
        if (normal.y <= EPSILON) {
            return sampledCenterHeight;
        }
        Vec3 centerToPivot = rig.renderedFootPivotFromCenter();
        return sampledCenterHeight
                - (normal.x * centerToPivot.x
                                + normal.z * centerToPivot.z)
                        / normal.y;
    }

    public record Quaternion(double x, double y, double z, double w) {
        public static final Quaternion IDENTITY =
                new Quaternion(0.0, 0.0, 0.0, 1.0);

        public Quaternion {
            double length = Math.sqrt(x * x + y * y + z * z + w * w);
            if (length <= EPSILON) {
                x = 0.0;
                y = 0.0;
                z = 0.0;
                w = 1.0;
            } else {
                x /= length;
                y /= length;
                z /= length;
                w /= length;
            }
        }

        public Quaternion multiply(Quaternion right) {
            return new Quaternion(
                    this.w * right.x
                            + this.x * right.w
                            + this.y * right.z
                            - this.z * right.y,
                    this.w * right.y
                            - this.x * right.z
                            + this.y * right.w
                            + this.z * right.x,
                    this.w * right.z
                            + this.x * right.y
                            - this.y * right.x
                            + this.z * right.w,
                    this.w * right.w
                            - this.x * right.x
                            - this.y * right.y
                            - this.z * right.z);
        }

        public Quaternion inverse() {
            return new Quaternion(-this.x, -this.y, -this.z, this.w);
        }

        public Quaternion slerp(Quaternion target, float alpha) {
            if (alpha <= 0.0F) {
                return this;
            }
            if (alpha >= 1.0F) {
                return target;
            }

            double targetX = target.x;
            double targetY = target.y;
            double targetZ = target.z;
            double targetW = target.w;
            double dot = this.x * targetX
                    + this.y * targetY
                    + this.z * targetZ
                    + this.w * targetW;
            if (dot < 0.0) {
                dot = -dot;
                targetX = -targetX;
                targetY = -targetY;
                targetZ = -targetZ;
                targetW = -targetW;
            }

            if (dot > 0.9995) {
                return new Quaternion(
                        Mth.lerp(alpha, this.x, targetX),
                        Mth.lerp(alpha, this.y, targetY),
                        Mth.lerp(alpha, this.z, targetZ),
                        Mth.lerp(alpha, this.w, targetW));
            }

            double theta = Math.acos(Mth.clamp(dot, -1.0, 1.0));
            double sineTheta = Math.sin(theta);
            double ownWeight = Math.sin((1.0 - alpha) * theta) / sineTheta;
            double targetWeight = Math.sin(alpha * theta) / sineTheta;
            return new Quaternion(
                    this.x * ownWeight + targetX * targetWeight,
                    this.y * ownWeight + targetY * targetWeight,
                    this.z * ownWeight + targetZ * targetWeight,
                    this.w * ownWeight + targetW * targetWeight);
        }

        public Vec3 rotate(Vec3 vector) {
            Vec3 quaternionVector = new Vec3(this.x, this.y, this.z);
            Vec3 twiceCross = quaternionVector.cross(vector).scale(2.0);
            return vector
                    .add(twiceCross.scale(this.w))
                    .add(quaternionVector.cross(twiceCross));
        }

        public DinosaurBoneRotation toEuler() {
            double m00 = 1.0 - 2.0 * (this.y * this.y + this.z * this.z);
            double m10 = 2.0 * (this.x * this.y + this.w * this.z);
            double m20 = 2.0 * (this.x * this.z - this.w * this.y);
            double m11 = 1.0 - 2.0 * (this.x * this.x + this.z * this.z);
            double m12 = 2.0 * (this.y * this.z - this.w * this.x);
            double m21 = 2.0 * (this.y * this.z + this.w * this.x);
            double m22 = 1.0 - 2.0 * (this.x * this.x + this.y * this.y);

            double yRadians = Math.asin(Mth.clamp(-m20, -1.0, 1.0));
            double cosineY = Math.cos(yRadians);
            double xRadians;
            double zRadians;
            if (Math.abs(cosineY) > EPSILON) {
                xRadians = Math.atan2(m21, m22);
                zRadians = Math.atan2(m10, m00);
            } else {
                xRadians = Math.atan2(-m12, m11);
                zRadians = 0.0;
            }
            return new DinosaurBoneRotation(
                    wrapRadians(xRadians),
                    wrapRadians(yRadians),
                    wrapRadians(zRadians));
        }

    }

    private static float wrapRadians(double angle) {
        while (angle <= -Math.PI) {
            angle += Math.PI * 2.0;
        }
        while (angle > Math.PI) {
            angle -= Math.PI * 2.0;
        }
        return (float) angle;
    }
}
