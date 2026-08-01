package com.wachi.mse.client.animation;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.renderer.base.BoneSnapshots;
import com.wachi.mse.entity.dinosaur.config.DinosaurLegRig;
import com.wachi.mse.entity.dinosaur.config.DinosaurLookBone;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBoneRotation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegIkSolver;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurLegPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurOrientationPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurRotationMath;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurRotationMath.Quaternion;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DinosaurProceduralAnimator {
    private static final double MAX_SMOOTHING_GAP_TICKS = 5.0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;
    private static final float FOOT_TILT_CONTACT_WEIGHT = 0.65F;
    private static final float MAX_FOOT_ANGULAR_SPEED_RADIANS_PER_SECOND =
            (float) Math.toRadians(240.0);

    private final Map<LivingEntity, SmoothedPose> smoothedPoses = new WeakHashMap<>();

    public DinosaurProceduralPose smooth(
            LivingEntity entity,
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            float partialTick) {
        double renderTime = entity.level().getGameTime() + partialTick;
        SmoothedPose previous = this.smoothedPoses.get(entity);
        if (previous == null
                || Float.compare(previous.scale(), config.scale()) != 0
                || renderTime < previous.renderTime()
                || renderTime - previous.renderTime() > MAX_SMOOTHING_GAP_TICKS) {
            float bodyTranslationY =
                    target.targetBodyTranslationYBlocks();
            Map<String, SmoothedFootTilt> footTilts =
                    updateFootTilts(
                            null,
                            target,
                            config,
                            0.0);
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    target.targetPitchRadians()
                            - target.targetBalancePitchRadians(),
                    target.targetRollRadians()
                            - target.targetBalanceRollRadians(),
                    footTilts);
            SmoothedPose initial = new SmoothedPose(
                    target.targetPitchRadians(),
                    target.targetRollRadians(),
                    bodyTranslationY,
                    target.targetBalancePitchRadians(),
                    target.targetBalanceRollRadians(),
                    target.orientation(),
                    footTilts,
                    config.scale(),
                    renderTime);
            this.smoothedPoses.put(entity, initial);
            return target.withSmoothedValues(
                    initial.pitch(),
                    initial.roll(),
                    initial.bodyTranslationY(),
                    initial.balancePitch(),
                    initial.balanceRoll(),
                    initial.orientation(),
                    legs);
        }
        if (renderTime == previous.renderTime()) {
            float structuralPitch =
                    previous.pitch() - previous.balancePitch();
            float structuralRoll =
                    previous.roll() - previous.balanceRoll();
            float bodyTranslationY = previous.bodyTranslationY();
            Map<String, SmoothedFootTilt> footTilts =
                    updateFootTilts(
                            previous.footTilts(),
                            target,
                            config,
                            0.0);
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    structuralPitch,
                    structuralRoll,
                    footTilts);
            this.smoothedPoses.put(
                    entity,
                    new SmoothedPose(
                            previous.pitch(),
                            previous.roll(),
                            bodyTranslationY,
                            previous.balancePitch(),
                            previous.balanceRoll(),
                            previous.orientation(),
                            footTilts,
                            config.scale(),
                            renderTime));
            return target.withSmoothedValues(
                    previous.pitch(),
                    previous.roll(),
                    bodyTranslationY,
                    previous.balancePitch(),
                    previous.balanceRoll(),
                    previous.orientation(),
                    legs);
        }

        double elapsedSeconds = (renderTime - previous.renderTime()) / 20.0;
        float alpha = (float) (1.0 - Math.exp(-config.smoothingResponsePerSecond() * elapsedSeconds));
        float pitch = lerp(previous.pitch(), target.targetPitchRadians(), alpha);
        float roll = lerp(previous.roll(), target.targetRollRadians(), alpha);
        float balancePitch = lerp(
                previous.balancePitch(),
                target.targetBalancePitchRadians(),
                alpha);
        float balanceRoll = lerp(
                previous.balanceRoll(),
                target.targetBalanceRollRadians(),
                alpha);
        float structuralPitch = pitch - balancePitch;
        float structuralRoll = roll - balanceRoll;
        float desiredBodyTranslationY = lerp(
                previous.bodyTranslationY(),
                target.targetBodyTranslationYBlocks(),
                alpha);
        float bodyTranslationY = desiredBodyTranslationY;
        float orientationAlpha = (float) (1.0 - Math.exp(
                -config.orientation().visualSmoothingResponsePerSecond()
                        * elapsedSeconds));
        DinosaurOrientationPose orientation = target.orientation().withSmoothedValues(
                previous.orientation().yawRadians()
                        + angularDelta(
                                previous.orientation().yawRadians(),
                                target.orientation().targetYawRadians())
                                * orientationAlpha,
                lerp(
                        previous.orientation().pitchRadians(),
                                target.orientation().targetPitchRadians(),
                                orientationAlpha));
        Map<String, SmoothedFootTilt> footTilts =
                updateFootTilts(
                        previous.footTilts(),
                        target,
                        config,
                        elapsedSeconds);
        List<DinosaurLegPose> legs = solveVisualLegs(
                target,
                config,
                bodyTranslationY,
                structuralPitch,
                structuralRoll,
                footTilts);
        this.smoothedPoses.put(
                entity,
                new SmoothedPose(
                        pitch,
                        roll,
                        bodyTranslationY,
                        balancePitch,
                        balanceRoll,
                        orientation,
                        footTilts,
                        config.scale(),
                        renderTime));
        return target.withSmoothedValues(
                pitch,
                roll,
                bodyTranslationY,
                balancePitch,
                balanceRoll,
                orientation,
                legs);
    }

    public void apply(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        float animationBodyY = snapshots
                .get(config.bodyBone())
                .map(snapshot -> applyBodyPose(snapshot, pose, config))
                .orElse(0.0F);
        for (DinosaurLegPose leg : pose.legs()) {
            float ikWeight;
            if (pose.airborne() || leg.forcedMaximumExtension()) {
                ikWeight = 1.0F;
            } else if (!leg.terrainContact() || !leg.reachable()) {
                // A ray may see distant terrain that this leg cannot reach.
                // It is observation, not support, and must never freeze an
                // authored foot in mid-air.
                ikWeight = 0.0F;
            } else {
                ikWeight = pose.gait().supportWeight(leg.legId());
            }
            applyLegPose(
                    config,
                    config.leg(leg.legId()),
                    compensateBodyAnimation(config, leg, pose, animationBodyY),
                    pose,
                    snapshots,
                    ikWeight);
        }
        applyLookPose(pose, config, snapshots);
    }

    private static void applyLookPose(
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config,
            BoneSnapshots snapshots) {
        float pitchRelativeToTiltedBody =
                pose.orientation().pitchRadians() - pose.pitchRadians();
        for (DinosaurLookBone bone : config.orientation().lookBones()) {
            snapshots.get(bone.boneName()).ifPresent(snapshot -> {
                snapshot.setRotY(
                        snapshot.getRotY()
                                - pose.orientation().yawRadians() * bone.yawWeight());
                snapshot.setRotX(
                        snapshot.getRotX()
                                + pitchRelativeToTiltedBody * bone.pitchWeight());
            });
        }
    }

    private static float applyBodyPose(
            BoneSnapshot body,
            DinosaurProceduralPose pose,
            DinosaurProceduralConfig config) {
        float animationBodyY =
                body.getTranslateY()
                        / MODEL_UNITS_PER_BLOCK
                        * config.scale();
        body.setTranslateY(
                body.getTranslateY()
                        + pose.bodyTranslationYBlocks()
                                * MODEL_UNITS_PER_BLOCK
                                / config.scale());
        body.setRotX(body.getRotX() + pose.pitchRadians());
        body.setRotZ(body.getRotZ() + pose.rollRadians());
        return animationBodyY;
    }

    private static void applyLegPose(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurLegPose legPose,
            DinosaurProceduralPose bodyPose,
            BoneSnapshots snapshots,
            float ikWeight) {
        snapshots.get(rig.upperBone()).ifPresent(snapshot -> {
            Vec3 compensation = legRootCompensation(
                    config,
                    rig,
                    bodyPose);
            snapshot.setTranslateX(
                    snapshot.getTranslateX()
                            + (float) compensation.x
                                    * MODEL_UNITS_PER_BLOCK
                                    / config.scale());
            snapshot.setTranslateY(
                    snapshot.getTranslateY()
                            + (float) compensation.y
                                    * MODEL_UNITS_PER_BLOCK
                                    / config.scale());
            snapshot.setTranslateZ(
                    snapshot.getTranslateZ()
                            + (float) compensation.z
                                    * MODEL_UNITS_PER_BLOCK
                                    / config.scale());
            snapshot.setRotX(
                    snapshot.getRotX() - bodyPose.balancePitchRadians());
            snapshot.setRotZ(
                    snapshot.getRotZ() - bodyPose.balanceRollRadians());
            blendRotation(
                    snapshot,
                    compensatedHipRotation(legPose, bodyPose),
                    ikWeight);
        });
        snapshots.get(rig.lowerBone()).ifPresent(snapshot ->
                blendRotation(snapshot, legPose.kneeRotation(), ikWeight));
        snapshots.get(rig.footBone()).ifPresent(snapshot ->
                blendFootOrientation(
                        config,
                        rig,
                        snapshot,
                        legPose,
                        ikWeight,
                        snapshots));
    }

    private static DinosaurBoneRotation compensatedHipRotation(
            DinosaurLegPose legPose,
            DinosaurProceduralPose bodyPose) {
        DinosaurBoneRotation hip = legPose.hipRotation();
        return new DinosaurBoneRotation(
                hip.xRadians() - bodyPose.balancePitchRadians(),
                hip.yRadians(),
                hip.zRadians() - bodyPose.balanceRollRadians());
    }

    /**
     * Keeps each hip at the position produced by structural terrain tilt even
     * though its parent body also carries a locomotion-only weight shift.
     */
    public static Vec3 legRootCompensation(
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

    private static Vec3 rotateX(Vec3 point, float angle) {
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(
                point.x,
                point.y * cosine - point.z * sine,
                point.y * sine + point.z * cosine);
    }

    private static Vec3 rotateZ(Vec3 point, float angle) {
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(
                point.x * cosine - point.y * sine,
                point.x * sine + point.y * cosine,
                point.z);
    }

    /**
     * Blends from the authored clip pose to an absolute IK rotation relative
     * to the neutral rig. Adding both full rotations would make the animation
     * and IK solve the same joint twice.
     */
    private static void blendRotation(
            BoneSnapshot snapshot,
            DinosaurBoneRotation rotation,
            float ikWeight) {
        snapshot.setRotX(blendAngle(
                snapshot.getRotX(),
                rotation.xRadians(),
                ikWeight));
        snapshot.setRotY(blendAngle(
                snapshot.getRotY(),
                rotation.yRadians(),
                ikWeight));
        snapshot.setRotZ(blendAngle(
                snapshot.getRotZ(),
                rotation.zRadians(),
                ikWeight));
    }

    /**
     * Blends the complete foot orientation in model-root space. The old
     * component-wise Euler blend ignored the already blended rotations of the
     * body, upper leg and lower leg, so their cancellation failed halfway
     * through a step and produced apparently random foot angles.
     */
    private static void blendFootOrientation(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            BoneSnapshot snapshot,
            DinosaurLegPose pose,
            float ikWeight,
            BoneSnapshots snapshots) {
        Quaternion parentWorld = DinosaurRotationMath.parentWorldRotation(
                config.skeleton(),
                rig.footBone(),
                boneName -> snapshots.get(boneName)
                        .map(DinosaurProceduralAnimator::totalRotation)
                        .orElse(DinosaurBoneRotation.ZERO));
        DinosaurBoneRotation authoredLocalRotation = totalRotation(snapshot);
        Quaternion authoredWorld = parentWorld.multiply(
                DinosaurRotationMath.fromEuler(authoredLocalRotation));
        Quaternion terrainWorld = DinosaurRotationMath.terrainOrientation(
                pose.footTerrainPitchRadians(),
                pose.footTerrainRollRadians());
        Quaternion blendedWorld = authoredWorld.slerp(
                terrainWorld,
                ikWeight);
        DinosaurBoneRotation blendedLocal = parentWorld
                .inverse()
                .multiply(blendedWorld)
                .toEuler();

        snapshot.setRotX(
                blendedLocal.xRadians() - snapshot.getBone().baseRotX());
        snapshot.setRotY(
                blendedLocal.yRadians() - snapshot.getBone().baseRotY());
        snapshot.setRotZ(
                blendedLocal.zRadians() - snapshot.getBone().baseRotZ());

        /*
         * Slerping a long box between two valid orientations is nonlinear:
         * an intermediate corner can dip below the support plane even when
         * both endpoints fit. Lift only that residual amount. Full authored
         * and full IK poses remain untouched, and the correction scales from
         * the real foot box rather than a species-specific constant.
         */
        double authoredClearance = DinosaurRotationMath.footPivotClearance(
                rig,
                authoredWorld,
                terrainWorld);
        double proceduralClearance =
                DinosaurRotationMath.footPivotClearance(
                        rig,
                        terrainWorld,
                        terrainWorld);
        double blendedClearance = DinosaurRotationMath.footPivotClearance(
                rig,
                blendedWorld,
                terrainWorld);
        double interpolatedClearance = Mth.lerp(
                ikWeight,
                authoredClearance,
                proceduralClearance);
        double residualLift = Math.max(
                0.0,
                blendedClearance - interpolatedClearance);
        if (residualLift <= 1.0E-6) {
            return;
        }

        Vec3 localLift = parentWorld
                .inverse()
                .rotate(new Vec3(0.0, residualLift, 0.0));
        double modelUnits = MODEL_UNITS_PER_BLOCK / config.scale();
        snapshot.setTranslateX(
                snapshot.getTranslateX()
                        - (float) (localLift.x * modelUnits));
        snapshot.setTranslateY(
                snapshot.getTranslateY()
                        + (float) (localLift.y * modelUnits));
        snapshot.setTranslateZ(
                snapshot.getTranslateZ()
                        + (float) (localLift.z * modelUnits));
    }

    private static DinosaurBoneRotation totalRotation(
            BoneSnapshot snapshot) {
        return new DinosaurBoneRotation(
                snapshot.getBone().baseRotX() + snapshot.getRotX(),
                snapshot.getBone().baseRotY() + snapshot.getRotY(),
                snapshot.getBone().baseRotZ() + snapshot.getRotZ());
    }

    private static DinosaurLegPose compensateBodyAnimation(
            DinosaurProceduralConfig config,
            DinosaurLegPose smoothedLeg,
            DinosaurProceduralPose pose,
            float animationBodyY) {
        if (Math.abs(animationBodyY) < 1.0E-5F) {
            return smoothedLeg;
        }

        DinosaurLegRig rig = config.leg(smoothedLeg.legId());
        return solveForBodyHeight(
                config,
                rig,
                smoothedLeg,
                pose.bodyTranslationYBlocks() + animationBodyY,
                pose.structuralPitchRadians(),
                pose.structuralRollRadians())
                .withFootTerrainTilt(
                        smoothedLeg.footTerrainPitchRadians(),
                        smoothedLeg.footTerrainRollRadians());
    }

    private static DinosaurLegPose solveForBodyHeight(
            DinosaurProceduralConfig config,
            DinosaurLegRig rig,
            DinosaurLegPose reference,
            float bodyTranslationY,
            float bodyPitch,
            float bodyRoll) {
        if (reference.forcedMaximumExtension()) {
            return DinosaurLegIkSolver.solveMaximumExtension(
                    config,
                    rig,
                    bodyTranslationY,
                    bodyPitch,
                    bodyRoll,
                    reference.terrainContact())
                    .withRequestedState(reference);
        }
        return DinosaurLegIkSolver.solveTarget(
                config,
                rig,
                reference.targetFootHeightOffset(),
                bodyTranslationY,
                bodyPitch,
                bodyRoll,
                reference.planted(),
                reference.terrainContact());
    }

    /**
     * Solves every leg against the exact body transform that will be rendered.
     * Interpolating nonlinear IK bone angles independently from the body can
     * never preserve a planted contact, so body smoothing happens first and
     * this pass reconstructs all limb rotations coherently afterwards.
     */
    private static List<DinosaurLegPose> solveVisualLegs(
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            float bodyTranslationY,
            float bodyPitch,
            float bodyRoll,
            Map<String, SmoothedFootTilt> footTilts) {
        Map<String, DinosaurTerrainSample> samplesByLegId = new HashMap<>();
        for (DinosaurTerrainSample sample : target.samples()) {
            samplesByLegId.put(sample.legId(), sample);
        }

        List<DinosaurLegPose> result =
                new ArrayList<>(target.legs().size());
        for (DinosaurLegPose targetLeg : target.legs()) {
            DinosaurTerrainSample sample =
                    samplesByLegId.get(targetLeg.legId());
            SmoothedFootTilt footTilt =
                    footTilts.get(targetLeg.legId());
            boolean planted = sample != null
                    ? sample.plantedCandidate()
                    : targetLeg.planted();
            float footPitch = footTilt != null
                    ? footTilt.pitchRadians()
                    : targetLeg.footTerrainPitchRadians();
            float footRoll = footTilt != null
                    ? footTilt.rollRadians()
                    : targetLeg.footTerrainRollRadians();
            DinosaurLegRig rig = config.leg(targetLeg.legId());
            double requestedClearance =
                    DinosaurRotationMath.terrainFootPivotClearance(
                            rig,
                            targetLeg.footTerrainPitchRadians(),
                            targetLeg.footTerrainRollRadians());
            double smoothedClearance =
                    DinosaurRotationMath.terrainFootPivotClearance(
                            rig,
                            footPitch,
                            footRoll);
            float targetFootHeight = (float) (
                    targetLeg.targetFootHeightOffset()
                            - requestedClearance
                            + smoothedClearance);
            if (targetLeg.forcedMaximumExtension()) {
                DinosaurLegPose extended =
                        DinosaurLegIkSolver.solveMaximumExtension(
                                config,
                                rig,
                                bodyTranslationY,
                                bodyPitch,
                                bodyRoll,
                                targetLeg.terrainContact());
                result.add(extended
                        .withRequestedState(targetLeg)
                        .withFootTerrainTilt(footPitch, footRoll));
                continue;
            }
            result.add(DinosaurLegIkSolver.solveTarget(
                    config,
                    rig,
                    targetFootHeight,
                    bodyTranslationY,
                    bodyPitch,
                    bodyRoll,
                    planted,
                    targetLeg.terrainContact())
                    .withFootTerrainTilt(
                            footPitch,
                            footRoll));
        }
        return List.copyOf(result);
    }

    /**
     * Smooths only the visual terrain normal. Foot height always comes from
     * the current sample: retaining an old world-space height while the body
     * moved horizontally was the source of hovering and clipping when walking
     * backwards or crossing a ledge.
     */
    private static Map<String, SmoothedFootTilt> updateFootTilts(
            Map<String, SmoothedFootTilt> previousTilts,
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            double elapsedSeconds) {
        Map<String, DinosaurTerrainSample> samplesByLegId = new HashMap<>();
        for (DinosaurTerrainSample sample : target.samples()) {
            samplesByLegId.put(sample.legId(), sample);
        }

        Map<String, SmoothedFootTilt> result = new HashMap<>();
        for (DinosaurLegPose leg : target.legs()) {
            DinosaurTerrainSample sample = samplesByLegId.get(leg.legId());
            SmoothedFootTilt previous = previousTilts == null
                    ? null
                    : previousTilts.get(leg.legId());
            float supportWeight =
                    target.gait().supportWeight(leg.legId());
            boolean stableContact = sample != null
                    && sample.valid()
                    && leg.terrainContact()
                    && leg.reachable()
                    && !leg.forcedMaximumExtension()
                    && supportWeight >= FOOT_TILT_CONTACT_WEIGHT;
            float targetPitch =
                    stableContact ? sample.footPitchRadians() : 0.0F;
            float targetRoll =
                    stableContact ? sample.footRollRadians() : 0.0F;

            float currentPitch = previous == null
                    ? targetPitch
                    : smoothFootAngle(
                            previous.pitchRadians(),
                            targetPitch,
                            config.smoothingResponsePerSecond(),
                            elapsedSeconds);
            float currentRoll = previous == null
                    ? targetRoll
                    : smoothFootAngle(
                            previous.rollRadians(),
                            targetRoll,
                            config.smoothingResponsePerSecond(),
                            elapsedSeconds);
            result.put(
                    leg.legId(),
                    new SmoothedFootTilt(
                            currentPitch,
                            currentRoll));
        }
        return Map.copyOf(result);
    }

    private static float smoothFootAngle(
            float current,
            float target,
            float responsePerSecond,
            double elapsedSeconds) {
        if (elapsedSeconds <= 0.0) {
            return current;
        }
        float alpha = (float) (
                1.0 - Math.exp(-responsePerSecond * elapsedSeconds));
        float desiredStep = angularDelta(current, target) * alpha;
        float maximumStep = (float) (
                MAX_FOOT_ANGULAR_SPEED_RADIANS_PER_SECOND
                        * elapsedSeconds);
        return current
                + Mth.clamp(desiredStep, -maximumStep, maximumStep);
    }

    private static float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    private static float angularDelta(float start, float end) {
        float delta = end - start;
        while (delta <= -Math.PI) {
            delta += (float) (Math.PI * 2.0);
        }
        while (delta > Math.PI) {
            delta -= (float) (Math.PI * 2.0);
        }
        return delta;
    }

    private static float blendAngle(
            float authored,
            float procedural,
            float proceduralWeight) {
        if (proceduralWeight <= 0.0F) {
            return authored;
        }
        if (proceduralWeight >= 1.0F) {
            return procedural;
        }
        return authored
                + angularDelta(authored, procedural)
                        * proceduralWeight;
    }

    private record SmoothedPose(
            float pitch,
            float roll,
            float bodyTranslationY,
            float balancePitch,
            float balanceRoll,
            DinosaurOrientationPose orientation,
            Map<String, SmoothedFootTilt> footTilts,
            float scale,
            double renderTime) {
        private SmoothedPose {
            footTilts = Map.copyOf(footTilts);
        }
    }

    private record SmoothedFootTilt(
            float pitchRadians,
            float rollRadians) {
    }
}
