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
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DinosaurProceduralAnimator {
    private static final double MAX_SMOOTHING_GAP_TICKS = 5.0;
    private static final float MODEL_UNITS_PER_BLOCK = 16.0F;

    private final Map<LivingEntity, SmoothedPose> smoothedPoses = new WeakHashMap<>();

    public DinosaurProceduralPose smooth(
            LivingEntity entity,
            DinosaurProceduralPose target,
            DinosaurProceduralConfig config,
            float partialTick) {
        double renderTime = entity.level().getGameTime() + partialTick;
        SmoothedPose previous = this.smoothedPoses.get(entity);
        if (previous == null
                || renderTime < previous.renderTime()
                || renderTime - previous.renderTime() > MAX_SMOOTHING_GAP_TICKS) {
            float bodyTranslationY =
                    DinosaurLegIkSolver.constrainBodyTranslationY(
                            config,
                            target.samples(),
                            target.legs(),
                            target.targetPitchRadians()
                                    - target.targetBalancePitchRadians(),
                            target.targetRollRadians()
                                    - target.targetBalanceRollRadians(),
                            target.targetBodyTranslationYBlocks());
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    target.targetPitchRadians()
                            - target.targetBalancePitchRadians(),
                    target.targetRollRadians()
                            - target.targetBalanceRollRadians());
            SmoothedPose initial = new SmoothedPose(
                    target.targetPitchRadians(),
                    target.targetRollRadians(),
                    bodyTranslationY,
                    target.targetBalancePitchRadians(),
                    target.targetBalanceRollRadians(),
                    target.orientation(),
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
            float bodyTranslationY =
                    DinosaurLegIkSolver.constrainBodyTranslationY(
                            config,
                            target.samples(),
                            target.legs(),
                            structuralPitch,
                            structuralRoll,
                            previous.bodyTranslationY());
            List<DinosaurLegPose> legs = solveVisualLegs(
                    target,
                    config,
                    bodyTranslationY,
                    structuralPitch,
                    structuralRoll);
            this.smoothedPoses.put(
                    entity,
                    new SmoothedPose(
                            previous.pitch(),
                            previous.roll(),
                            bodyTranslationY,
                            previous.balancePitch(),
                            previous.balanceRoll(),
                            previous.orientation(),
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
        float bodyTranslationY =
                DinosaurLegIkSolver.constrainBodyTranslationY(
                        config,
                        target.samples(),
                        target.legs(),
                        structuralPitch,
                        structuralRoll,
                        desiredBodyTranslationY);
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
        List<DinosaurLegPose> legs = solveVisualLegs(
                target,
                config,
                bodyTranslationY,
                structuralPitch,
                structuralRoll);
        this.smoothedPoses.put(
                entity,
                new SmoothedPose(
                        pitch,
                        roll,
                        bodyTranslationY,
                        balancePitch,
                        balanceRoll,
                        orientation,
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
            float ikWeight = leg.forcedMaximumExtension()
                    ? 1.0F
                    : pose.gait().supportWeight(leg.legId());
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
                blendFootRotation(snapshot, legPose, ikWeight));
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

    private static void blendFootRotation(
            BoneSnapshot snapshot,
            DinosaurLegPose pose,
            float ikWeight) {
        DinosaurBoneRotation foot = pose.footRotation();
        snapshot.setRotX(blendAngle(
                snapshot.getRotX(),
                foot.xRadians() + pose.footTerrainPitchRadians(),
                ikWeight));
        snapshot.setRotY(blendAngle(
                snapshot.getRotY(),
                foot.yRadians(),
                ikWeight));
        snapshot.setRotZ(blendAngle(
                snapshot.getRotZ(),
                foot.zRadians() + pose.footTerrainRollRadians(),
                ikWeight));
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
            float bodyRoll) {
        Map<String, DinosaurTerrainSample> samplesByLegId = new HashMap<>();
        for (DinosaurTerrainSample sample : target.samples()) {
            samplesByLegId.put(sample.legId(), sample);
        }

        List<DinosaurLegPose> result =
                new ArrayList<>(target.legs().size());
        for (DinosaurLegPose targetLeg : target.legs()) {
            DinosaurTerrainSample sample =
                    samplesByLegId.get(targetLeg.legId());
            boolean planted = sample != null
                    ? sample.plantedCandidate()
                    : targetLeg.planted();
            if (targetLeg.forcedMaximumExtension()) {
                DinosaurLegPose extended =
                        DinosaurLegIkSolver.solveMaximumExtension(
                                config,
                                config.leg(targetLeg.legId()),
                                bodyTranslationY,
                                bodyPitch,
                                bodyRoll,
                                targetLeg.terrainContact());
                result.add(extended.withRequestedState(targetLeg));
                continue;
            }
            result.add(DinosaurLegIkSolver.solveTarget(
                    config,
                    config.leg(targetLeg.legId()),
                    targetLeg.targetFootHeightOffset(),
                    bodyTranslationY,
                    bodyPitch,
                    bodyRoll,
                    planted,
                    targetLeg.terrainContact())
                    .withFootTerrainTilt(
                            targetLeg.footTerrainPitchRadians(),
                            targetLeg.footTerrainRollRadians()));
        }
        return List.copyOf(result);
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
            double renderTime) {
    }
}
