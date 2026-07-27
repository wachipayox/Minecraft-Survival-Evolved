package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Native NeoForge multipart broad-phase entry for a logical dinosaur region.
 */
public final class DinosaurPartEntity
        extends net.neoforged.neoforge.entity.PartEntity<PrototypeDinosaurEntity> {
    private static final EntityDimensions PLACEHOLDER_SIZE =
            EntityDimensions.fixed(1.0F, 1.0F);

    private final DinosaurSkeletonConfig.HitboxPart partConfig;

    public DinosaurPartEntity(
            PrototypeDinosaurEntity parent,
            DinosaurSkeletonConfig.HitboxPart partConfig) {
        super(parent);
        this.partConfig = partConfig;
        this.refreshDimensions();
    }

    public DinosaurSkeletonConfig.HitboxPart partConfig() {
        return this.partConfig;
    }

    public void setPartBounds(AABB bounds) {
        Vec3 oldPosition = this.position();
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        this.setPos(x, bounds.minY, z);
        this.setBoundingBox(bounds);
        this.xo = oldPosition.x;
        this.yo = oldPosition.y;
        this.zo = oldPosition.z;
        this.xOld = oldPosition.x;
        this.yOld = oldPosition.y;
        this.zOld = oldPosition.z;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public InteractionResult interact(
            Player player,
            InteractionHand hand,
            Vec3 location) {
        if (!this.getParent().canInteractWithPart(
                this.partConfig,
                player)) {
            return InteractionResult.PASS;
        }
        return this.getParent().interact(player, hand, location);
    }

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float damage) {
        return !this.isInvulnerableToBase(source)
                && this.getParent().hurtFromPart(
                        level,
                        this.partConfig,
                        source,
                        damage);
    }

    @Override
    public boolean is(Entity other) {
        return this == other || this.getParent() == other;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return PLACEHOLDER_SIZE;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
