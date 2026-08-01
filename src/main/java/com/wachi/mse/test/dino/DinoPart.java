package com.wachi.mse.test.dino;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.function.Function;

public class DinoPart extends PartEntity<DinoEntity> {

    private final EntityDimensions dimensions;
    private boolean positionInitialized = false;

    private final Function<AABB, Vec3> posCalculator;

    public DinoPart(DinoEntity parent, Function<AABB, Vec3> posCalculator, float width, float height) {
        super(parent);
        this.dimensions = EntityDimensions.scalable(width, height);
        this.refreshDimensions();

        this.posCalculator = posCalculator;
        updatePos();
    }

    public void updatePos(){
        var parentBox = getParent().getBoundingBox();
        var pos = posCalculator.apply(parentBox);

        if (!positionInitialized) {
            setPos(pos);
            setOldPosAndRot();
            positionInitialized = true;
            return;
        }

        setOldPosAndRot();
        setPos(pos);
    }

    @Override
    public boolean is(Entity entity) {
        return entity == this || entity == getParent();
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float v) {
        return getParent().hurtServer(serverLevel, damageSource, v);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.dimensions;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {

    }
}
