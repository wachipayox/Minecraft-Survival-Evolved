package com.wachi.mse.entity.dinosaur.procedural;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurStabilityConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Small deterministic 2D support-polygon solver. Its cost depends only on the
 * number of configured legs; four legs produce at most 32 input vertices.
 */
public final class DinosaurStabilitySolver {
    private static final double EPSILON = 1.0E-7;
    private static final int FOOT_CIRCLE_VERTICES = 8;

    private DinosaurStabilitySolver() {
    }

    public static DinosaurStabilityAssessment assess(
            DinosaurProceduralConfig config,
            Vec3 origin,
            float bodyYawDegrees,
            List<DinosaurTerrainSample> samples,
            List<DinosaurLegPose> legs,
            DinosaurFootprintSupport footprintSupport) {
        DinosaurStabilityConfig stability = config.stability();
        Vec3 centerOfMass = modelPointToWorld(
                origin,
                stability.centerOfMassModelX(),
                stability.centerOfMassModelZ(),
                bodyYawDegrees);
        Map<String, DinosaurTerrainSample> samplesByLeg = new HashMap<>();
        for (DinosaurTerrainSample sample : samples) {
            samplesByLeg.put(sample.legId(), sample);
        }

        List<Vec3> footprintVertices = new ArrayList<>();
        int supportingLegs = 0;
        Vec3 unsupportedBias = Vec3.ZERO;
        for (DinosaurLegPose leg : legs) {
            DinosaurTerrainSample sample = samplesByLeg.get(leg.legId());
            if (sample == null) {
                continue;
            }
            if (!leg.planted()) {
                double reachError = Math.abs(
                        leg.targetFootHeightOffset()
                                - leg.solvedFootHeightOffset());
                double missingGroundBias =
                        sample.valid() ? 0.0 : config.sampleBelow();
                double weight = 1.0 + reachError + missingGroundBias;
                unsupportedBias = unsupportedBias.add(
                        (sample.position().x - centerOfMass.x) * weight,
                        0.0,
                        (sample.position().z - centerOfMass.z) * weight);
                continue;
            }
            supportingLegs++;
            appendFootCircle(
                    footprintVertices,
                    sample.position(),
                    stability.footSupportRadius());
        }

        Vec3 footprintDirection = footprintSupport.present()
                ? horizontalDirection(
                        footprintSupport.centerWorld(),
                        centerOfMass)
                : Vec3.ZERO;
        List<Vec3> hull = convexHull(footprintVertices);
        if (hull.size() < 3) {
            if (supportingLegs == 0) {
                Vec3 fallDirection =
                        footprintDirection.lengthSqr() > EPSILON
                                ? footprintDirection
                                : unsupportedBias.lengthSqr() > EPSILON
                                        ? unsupportedBias.normalize()
                                        : modelForwardDirection(
                                                bodyYawDegrees);
                return DinosaurStabilityAssessment.fullyUnsupported(
                        centerOfMass,
                        fallDirection,
                        footprintSupport);
            }
            return DinosaurStabilityAssessment.notEvaluable(
                    centerOfMass,
                    supportingLegs,
                    footprintSupport);
        }

        BoundaryDistance boundary = distanceToBoundary(centerOfMass, hull);
        double signedMargin = boundary.inside()
                ? boundary.distance()
                : -boundary.distance();
        boolean stable =
                signedMargin >= -stability.toleratedOutsideDistance();
        Vec3 polygonDirection = boundary.inside()
                ? Vec3.ZERO
                : horizontalDirection(boundary.closestPoint(), centerOfMass);
        // The collision footprint is the terrain physically retaining the
        // AABB, so falling away from its centroid takes precedence. Missing
        // feet and the anatomical support polygon remain deterministic
        // fallbacks when no residual body support exists.
        Vec3 fallDirection = stable
                ? Vec3.ZERO
                : footprintDirection.lengthSqr() > EPSILON
                        ? footprintDirection
                        : unsupportedBias.lengthSqr() > EPSILON
                                ? unsupportedBias.normalize()
                                : polygonDirection;
        return new DinosaurStabilityAssessment(
                true,
                stable,
                signedMargin,
                centerOfMass,
                fallDirection,
                supportingLegs,
                hull,
                footprintSupport);
    }

    private static void appendFootCircle(
            List<Vec3> vertices,
            Vec3 center,
            double radius) {
        for (int index = 0; index < FOOT_CIRCLE_VERTICES; index++) {
            double angle = index * Math.PI * 2.0 / FOOT_CIRCLE_VERTICES;
            vertices.add(new Vec3(
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius));
        }
    }

    private static List<Vec3> convexHull(List<Vec3> input) {
        if (input.size() < 3) {
            return List.of();
        }
        List<Vec3> points = new ArrayList<>(input);
        points.sort(Comparator.comparingDouble((Vec3 point) -> point.x)
                .thenComparingDouble(point -> point.z));
        List<Vec3> hull = new ArrayList<>(points.size() * 2);
        for (Vec3 point : points) {
            while (hull.size() >= 2
                    && cross(
                                    hull.get(hull.size() - 2),
                                    hull.get(hull.size() - 1),
                                    point)
                            <= EPSILON) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }

        int lowerSize = hull.size();
        for (int index = points.size() - 2; index >= 0; index--) {
            Vec3 point = points.get(index);
            while (hull.size() > lowerSize
                    && cross(
                                    hull.get(hull.size() - 2),
                                    hull.get(hull.size() - 1),
                                    point)
                            <= EPSILON) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        hull.remove(hull.size() - 1);
        return List.copyOf(hull);
    }

    private static BoundaryDistance distanceToBoundary(
            Vec3 point,
            List<Vec3> hull) {
        boolean inside = true;
        double minimumDistanceSquared = Double.POSITIVE_INFINITY;
        Vec3 closest = hull.getFirst();
        for (int index = 0; index < hull.size(); index++) {
            Vec3 start = hull.get(index);
            Vec3 end = hull.get((index + 1) % hull.size());
            if (cross(start, end, point) < -EPSILON) {
                inside = false;
            }
            Vec3 candidate = closestPointOnSegment(point, start, end);
            double distanceSquared = horizontalDistanceSquared(point, candidate);
            if (distanceSquared < minimumDistanceSquared) {
                minimumDistanceSquared = distanceSquared;
                closest = candidate;
            }
        }
        return new BoundaryDistance(
                inside,
                Math.sqrt(minimumDistanceSquared),
                closest);
    }

    private static Vec3 closestPointOnSegment(
            Vec3 point,
            Vec3 start,
            Vec3 end) {
        double edgeX = end.x - start.x;
        double edgeZ = end.z - start.z;
        double lengthSquared = edgeX * edgeX + edgeZ * edgeZ;
        if (lengthSquared <= EPSILON) {
            return start;
        }
        double alpha = ((point.x - start.x) * edgeX
                        + (point.z - start.z) * edgeZ)
                / lengthSquared;
        alpha = Math.max(0.0, Math.min(1.0, alpha));
        return new Vec3(
                start.x + edgeX * alpha,
                point.y,
                start.z + edgeZ * alpha);
    }

    private static Vec3 horizontalDirection(Vec3 start, Vec3 end) {
        Vec3 difference = new Vec3(
                end.x - start.x,
                0.0,
                end.z - start.z);
        return difference.lengthSqr() > EPSILON
                ? difference.normalize()
                : Vec3.ZERO;
    }

    private static double horizontalDistanceSquared(Vec3 left, Vec3 right) {
        double x = left.x - right.x;
        double z = left.z - right.z;
        return x * x + z * z;
    }

    private static double cross(Vec3 origin, Vec3 left, Vec3 right) {
        return (left.x - origin.x) * (right.z - origin.z)
                - (left.z - origin.z) * (right.x - origin.x);
    }

    private static Vec3 modelPointToWorld(
            Vec3 origin,
            double modelX,
            double modelZ,
            float bodyYawDegrees) {
        double modelYawRadians = Math.toRadians(180.0F - bodyYawDegrees);
        double sinYaw = Math.sin(modelYawRadians);
        double cosYaw = Math.cos(modelYawRadians);
        double renderedModelX = -modelX;
        return new Vec3(
                origin.x + cosYaw * renderedModelX + sinYaw * modelZ,
                origin.y,
                origin.z - sinYaw * renderedModelX + cosYaw * modelZ);
    }

    private static Vec3 modelForwardDirection(float bodyYawDegrees) {
        Vec3 origin = Vec3.ZERO;
        return modelPointToWorld(origin, 0.0, -1.0, bodyYawDegrees)
                .subtract(origin)
                .normalize();
    }

    private record BoundaryDistance(
            boolean inside,
            double distance,
            Vec3 closestPoint) {
    }
}
