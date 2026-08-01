package com.wachi.mse.test.dino;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

public class DinoLegPair<T extends DinoEntity> {

    public final T parent;
    private final Vec3 center;

    private float yRot = 0;

    private final DinoLeg<T> leftLeg, rightLeg;

    final double legDistance;

    public DinoLegPair(T parent, DinoLegBendConfig bendConfig, Vec3 center, double legDistance, double upperLength, double lowerLength, double thickness) {
        this.center = center;
        this.legDistance = legDistance;
        this.parent = parent;

        this.leftLeg = new DinoLeg<>(
                this, bendConfig,
                new Vec3(legDistance, 0, 0),
                upperLength, lowerLength, thickness
                );

        this.rightLeg = new DinoLeg<>(
                this, bendConfig,
                new Vec3(-legDistance, 0, 0),
                upperLength, lowerLength, thickness
        );
    }

    public void tick(float pTicks) {
        getLegs().forEach(leg -> leg.tick(pTicks));
    }

    public void setYRot(float yRot) {
        this.yRot = yRot;
    }

    public float getBodyYRotInRadians(float partialTicks){
        var bodyRot = Mth.rotLerp(partialTicks, parent.yBodyRotO, parent.yBodyRot);
        return -bodyRot * Mth.DEG_TO_RAD;
    }

    public Vec3 getCenter(float partialTicks) {
        return parent.getPosition(partialTicks).add(center);
    }

    public float getYRot(){
        return yRot;
    }

    public DinoLeg<T> getLeftLeg() {
        return leftLeg;
    }

    public DinoLeg<T> getRightLeg() {
        return rightLeg;
    }

    public List<DinoLeg<T>> getLegs(){
        return Arrays.asList(leftLeg, rightLeg);
    }


}
