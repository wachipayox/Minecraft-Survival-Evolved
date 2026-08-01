package com.wachi.mse.test.dino;

import com.wachi.mse.test.collide.Capsule;
import com.wachi.mse.test.terrain.TerrainWatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DinoLeg<T extends DinoEntity> {

    public enum DLegState{CHILL, AIR, LANDING}

    public record DLegPose(
            float upperXRot,
            float kneeXRot
    ){}

    public record DLegGeometry(
            Vec3 upper,
            Vec3 knee,
            Vec3 end
    ) {
        public List<Capsule> getCollisions(double thickness){
            return List.of(
                new Capsule(upper(), knee(), thickness),
                new Capsule(knee(), end(), thickness)
            );
        }
    }

    DLegState state = DLegState.CHILL;

    protected Vec3 upperPos, landingTarget;

    public float kneeXRotTarget, upperXRotTarget;

    private final float minReach, maxReach;

    public double thickness, upperLength, lowerLength;

    public final DinoLegPair<T> parent;
    public final DinoLegBendConfig bends;

    public DLegPose actualPose;

    public DinoLeg(DinoLegPair<T> parent, DinoLegBendConfig bends, Vec3 upperPos, double upperLength, double lowerLength, double thickness) {
        this.parent = parent;
        this.upperPos = upperPos;

        this.upperLength = upperLength;
        this.bends = bends;
        this.lowerLength = lowerLength;

        this.minReach = getLegLengthAtBend(bends.maxBend);
        this.maxReach = getLegLengthAtBend(bends.minBend);

        this.thickness = thickness;

        actualPose = new DLegPose(0, 0);
    }

    public void tick(float pTicks) {
        DLegGeometry geometry = calculateGeometry(actualPose, pTicks);
        Vec3 hit = getGround(geometry.upper(), getGroundQueryMaxReach());
        if(hit != null) hit.add(0, thickness, 0); //makes sure bone doesn't touch ground

        double hitDistanceToEnd = hit == null ? 0 : hit.distanceTo(geometry.end());

        if(state.equals(DLegState.CHILL)){
            //should add here to check if block or motion changes to add an optimization layer
            //rn literally checks same as being in air
            if(hit == null || hitDistanceToEnd > 0.01 || !geometryFits(geometry))
                setState(DLegState.AIR);
        }
        if(state.equals(DLegState.AIR)){
            if(hit != null){
                setState(DLegState.LANDING);
                landingTarget = hit;
                targetBalancedBend(
                        getBendForReach(hit.distanceTo(geometry.upper()))
                );
            }
        }
        if(state.equals(DLegState.LANDING)){
            if(landingTarget == null) setState(DLegState.AIR);
            else {
                double distance = landingTarget.distanceTo(geometry.upper());
                if (
                        distance < minReach || distance > maxReach
                        || !geometryFits(calculateGeometry(upperXRotTarget, kneeXRotTarget, pTicks))
                ) setState(DLegState.AIR);
                else if(geometry.end().distanceTo(landingTarget) < 0.1){
                    setState(DLegState.CHILL);
                }
            }
        }

        var kneeXRot = actualPose.kneeXRot;
        if(kneeXRot != kneeXRotTarget){
            if(kneeXRot < kneeXRotTarget){
                kneeXRot += Mth.DEG_TO_RAD * 2;
                if(kneeXRot > kneeXRotTarget) kneeXRot = kneeXRotTarget;

            } else if(kneeXRot > kneeXRotTarget){
                kneeXRot -= Mth.DEG_TO_RAD * 2;
                if(kneeXRot < kneeXRotTarget) kneeXRot = kneeXRotTarget;
            }
            actualPose = new DLegPose(actualPose.upperXRot, kneeXRot);
        }

        var upperXRot = actualPose.upperXRot;
        if(upperXRot != upperXRotTarget){
            if(upperXRot < upperXRotTarget){
                upperXRot += Mth.DEG_TO_RAD;
                if(upperXRot > upperXRotTarget) upperXRot = upperXRotTarget;

            } else if(upperXRot > upperXRotTarget){
                upperXRot -= Mth.DEG_TO_RAD;
                if(upperXRot < upperXRotTarget) upperXRot = upperXRotTarget;
            }
            actualPose = new DLegPose(upperXRot, actualPose.kneeXRot);
        }
    }

    private void targetBalancedBend(float bend){
        kneeXRotTarget = bend * bends.bendDirection;
        upperXRotTarget = calculateBalancedUpperRot(kneeXRotTarget);
    }

    private float calculateBalancedUpperRot(float kneeBend) {
        return -(float) Math.atan2(
                lowerLength * Math.sin(kneeBend),
                upperLength + lowerLength * Math.cos(kneeBend)
        );
    }

    private void setState(DLegState state){
        this.state = state;

        if (this.state == DLegState.AIR) {
            landingTarget = null;
            targetBalancedBend(bends.hangingBend);
        }
    }

    public float getBendForReach(double distance) {
        double cosBend = (
                distance * distance
                        - upperLength * upperLength
                        - lowerLength * lowerLength
        ) / (2.0 * upperLength * lowerLength);

        // Evita errores como 1.0000000002 por precisión decimal.
        cosBend = Mth.clamp(cosBend, -1.0, 1.0);

        return (float) Math.acos(cosBend);
    }

    private float getLegLengthAtBend(float bendRadians){
        return (float) Math.sqrt(
                upperLength * upperLength
                        + lowerLength * lowerLength
                        + 2.0 * upperLength * lowerLength * Math.cos(bendRadians)
        );
    }

    public static double getGroundQueryEpsilon(){
        return 0.05;
    }

    private double getGroundQueryMaxReach(){
        return getOuterMaxReach() + getGroundQueryEpsilon();
    }

    private double getOuterMaxReach(){
        return maxReach + thickness;
    }

    private @Nullable Vec3 getGround(Vec3 pos, double maxDistance) {
        AABB searchAB = new AABB(pos, pos.add(0, -maxDistance, 0));
        return getWatcher().getCache().stream()
                .filter(searchAB::intersects)
                .map(ab -> ab.clip(searchAB.getMaxPosition(), searchAB.getMinPosition()).orElse(null))
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(pos::distanceTo))
                .orElse(null);

// OLD METHOD WITHOUT TERRAIN WATCHER
//        BlockHitResult hitResult = getDinoParent().level().clip(
//                new ClipContext(
//                        pos, pos.add(0, -maxDistance, 0),
//                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
//                        getDinoParent()
//                )
//        );
//        return hitResult.getType().equals(HitResult.Type.BLOCK) ? hitResult : null;
    }

    private TerrainWatcher getWatcher(){
        return getDinoParent().getTerrainWatchers().getOrDefault(this, null);
    }

    public AABB getBounds(){
        double hipRadius = Math.hypot(upperPos.x, upperPos.z);
        double maxReach = getOuterMaxReach() + hipRadius;

        Vec3 pivot = parent.getCenter(1).add(0, upperPos.y, 0);

        return new AABB(
                pivot.add(-maxReach, -getOuterMaxReach(), -maxReach),
                pivot.add(maxReach, thickness, maxReach)
        );
    }

    private BlockPos convertToBlockPos(Vec3 pos){
        return new BlockPos((int) pos.x(), (int) pos.y(), (int) pos.z());
    }

    public T getDinoParent(){
        return parent.parent;
    }

    private float getYRot(float partialTicks){
        return parent.getBodyYRotInRadians(partialTicks)
                + parent.getYRot()
                //+ (float) Math.PI for invert
        ;
    }

    public boolean geometryFits(DLegGeometry geometry){
        var capsules = geometry.getCollisions(Math.max(0.0, thickness - 0.001));
        var level = getDinoParent().level();

        for (Capsule capsule : capsules)
            for (var voxelShape : level.getBlockCollisions(getDinoParent(), capsule.bounds()))
                for (var blockAABB : voxelShape.toAabbs())
                    if(
                            blockAABB.inflate(capsule.radius())
                                    .clip(capsule.start(), capsule.end()).isPresent()
                    ) return false;

        return true;
    }

    public DLegGeometry calculateGeometry(float upperXRot, float kneeXRot, float partialTicks) {
        return calculateGeometry(new DLegPose(upperXRot, kneeXRot), partialTicks);
    }

    public DLegGeometry calculateGeometry(DLegPose pose, float pTick){
        Vec3 upper = getUpper(pTick);
        float yRot = getYRot(pTick);

        Vector3f kneeOffset = new Vector3f(
                0,
                (float) -upperLength,
                0
        )
                .rotateX(pose.upperXRot())
                .rotateY(yRot);

        Vec3 knee = upper.add(
                kneeOffset.x(),
                kneeOffset.y(),
                kneeOffset.z()
        );

        Vector3f endOffset = new Vector3f(
                0,
                (float) -lowerLength,
                0
        )
                .rotateX(pose.upperXRot() + pose.kneeXRot())
                .rotateY(yRot);

        Vec3 end = knee.add(
                endOffset.x(),
                endOffset.y(),
                endOffset.z()
        );

        return new DLegGeometry(upper, knee, end);
    }

    public Vec3 getUpper(float partialTicks) {
        var upperOffset = upperPos.toVector3f()
                .rotateY(getYRot(partialTicks));

        return parent.getCenter(partialTicks).add(upperOffset.x(), upperOffset.y(), upperOffset.z());
    }
}
