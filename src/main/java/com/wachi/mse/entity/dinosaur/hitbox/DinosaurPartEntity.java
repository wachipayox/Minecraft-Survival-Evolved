package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
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
        extends net.neoforged.neoforge.entity.PartEntity<DinosaurEntity> {
    private static final EntityDimensions PLACEHOLDER_SIZE =
            EntityDimensions.fixed(1.0F, 1.0F);
    private static final int SERVER_VETO_LIFETIME_TICKS = 5;
    private static final double VETO_TRANSIT_TOLERANCE = 0.15;
    private static final double COLLISION_PROBE_MARGIN = 0.15;
    private static final double MAX_PROBE_MOVEMENT = 2.0;
    private static final double EMBEDDED_EPSILON = 0.025;

    private final DinosaurSkeletonConfig.HitboxPart partConfig;
    private final DinosaurSkeletonConfig.BoneBox boxConfig;
    private final int partIndex;
    private final Map<UUID, Long> serverVetoExpirations =
            new HashMap<>();
    private int clientVetoedEntityId = -1;
    private long clientVetoExpirationTick = Long.MIN_VALUE;

    public DinosaurPartEntity(
            DinosaurEntity parent,
            DinosaurSkeletonConfig.HitboxPart partConfig,
            DinosaurSkeletonConfig.BoneBox boxConfig,
            int partIndex) {
        super(parent);
        this.partConfig = partConfig;
        this.boxConfig = boxConfig;
        this.partIndex = partIndex;
        this.refreshDimensions();
    }

    public DinosaurSkeletonConfig.HitboxPart partConfig() {
        return this.partConfig;
    }

    public int partIndex() {
        return this.partIndex;
    }

    public DinosaurSkeletonConfig.BoneBox boxConfig() {
        return this.boxConfig;
    }

    /// Client command crosshair looked entity's uuid is obtained by this method
    @Override
    public String getStringUUID() {
        return this.getParent().getStringUUID();
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
        return this.getParent().canBeWalkedOn()
                && this.getParent().isAlive()
                && other != null
                && other != this.getParent()
                && !this.getParent().hasPassenger(other)
                && !this.getParent().isPassengerOfSameVehicle(other)
                && !this.getParent().isActivelyTransporting(other)
                && !this.hasSupportCollisionVeto(other);
    }

    public boolean preciselySupports(Entity entity) {
        return this.getParent().preciselySupports(
                this,
                entity.getBoundingBox());
    }

    public boolean preciselyIntersects(AABB bounds) {
        return this.getParent().preciselyIntersects(
                this,
                bounds);
    }

    public boolean isBroadCollisionCandidate(Entity entity) {
        return this.isBroadCollisionCandidate(
                entity,
                collisionProbe(entity));
    }

    public boolean isBroadCollisionCandidate(
            Entity entity,
            AABB probe) {
        return this.getParent().canBeWalkedOn()
                && this.getParent().isAlive()
                && entity != this.getParent()
                && !this.getParent().hasPassenger(entity)
                && probe.intersects(this.getBoundingBox());
    }

    public void setClientSupportVeto(
            Player player,
            boolean vetoed) {
        if (!this.level().isClientSide()) {
            return;
        }
        long gameTime = this.level().getGameTime();
        boolean alreadyActive =
                this.clientVetoedEntityId == player.getId()
                        && this.clientVetoExpirationTick >= gameTime;
        if (vetoed
                && (this.isBroadCollisionCandidate(player)
                        || alreadyActive
                                && this.isInsideVetoTransit(player))) {
            this.clientVetoedEntityId = player.getId();
            this.clientVetoExpirationTick =
                    gameTime + SERVER_VETO_LIFETIME_TICKS;
        } else {
            this.clientVetoedEntityId = -1;
            this.clientVetoExpirationTick = Long.MIN_VALUE;
            if (alreadyActive) {
                this.pushAboveIfEmbedded(player);
            }
        }
    }

    public void setServerSupportVeto(
            ServerPlayer player,
            boolean vetoed) {
        if (this.level().isClientSide()) {
            return;
        }
        long gameTime = this.level().getGameTime();
        long currentExpiration = this.serverVetoExpirations
                .getOrDefault(player.getUUID(), Long.MIN_VALUE);
        boolean alreadyActive = currentExpiration >= gameTime;
        if (vetoed
                && (this.isBroadCollisionCandidate(player)
                        || alreadyActive
                                && this.isInsideVetoTransit(player))) {
            this.serverVetoExpirations.put(
                    player.getUUID(),
                    gameTime + SERVER_VETO_LIFETIME_TICKS);
        } else {
            this.serverVetoExpirations.remove(player.getUUID());
            if (alreadyActive) {
                this.pushAboveIfEmbedded(player);
            }
        }
    }

    public void purgeExpiredSupportVetoes(long gameTime) {
        if (!this.serverVetoExpirations.isEmpty()) {
            this.serverVetoExpirations.values().removeIf(
                    expiration -> expiration < gameTime);
        }
    }

    private boolean hasSupportCollisionVeto(Entity entity) {
        long gameTime = this.level().getGameTime();
        if (this.level().isClientSide()) {
            return entity instanceof Player player
                    && player.isLocalPlayer()
                    && this.clientVetoedEntityId == player.getId()
                    && this.clientVetoExpirationTick >= gameTime
                    && (this.isBroadCollisionCandidate(player)
                            || this.isInsideVetoTransit(player));
        }
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        long expiration = this.serverVetoExpirations
                .getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (expiration < gameTime) {
            this.serverVetoExpirations.remove(player.getUUID());
            return false;
        }
        return this.isBroadCollisionCandidate(player)
                || this.isInsideVetoTransit(player);
    }

    /**
     * Exposes the client's negative-only support authority to the transport
     * tracker as well as to vanilla collision queries.
     */
    public boolean isSupportCollisionVetoed(Entity entity) {
        return this.hasSupportCollisionVeto(entity);
    }

    private boolean isInsideVetoTransit(Entity entity) {
        return entity.getBoundingBox().intersects(
                this.getBoundingBox().inflate(
                        VETO_TRANSIT_TOLERANCE));
    }

    public boolean pushAboveIfEmbedded(Entity entity) {
        if (this.hasSupportCollisionVeto(entity)) {
            return false;
        }
        AABB entityBounds = entity.getBoundingBox();
        AABB bounds = this.getBoundingBox();
        double overlapX = Math.min(entityBounds.maxX, bounds.maxX)
                - Math.max(entityBounds.minX, bounds.minX);
        double overlapY = Math.min(entityBounds.maxY, bounds.maxY)
                - Math.max(entityBounds.minY, bounds.minY);
        double overlapZ = Math.min(entityBounds.maxZ, bounds.maxZ)
                - Math.max(entityBounds.minZ, bounds.minZ);
        if (overlapX <= EMBEDDED_EPSILON
                || overlapY <= EMBEDDED_EPSILON
                || overlapZ <= EMBEDDED_EPSILON
                || !this.preciselyIntersects(entityBounds)) {
            return false;
        }
        Vec3 surface = this.getParent().preciseTopSurface(
                this,
                entityBounds);
        if (surface == null) {
            return false;
        }
        double penetration = surface.y - entityBounds.minY;
        if (penetration <= EMBEDDED_EPSILON) {
            return false;
        }
        entity.move(
                MoverType.SHULKER,
                new Vec3(0.0, penetration + 0.01, 0.0));
        return true;
    }

    private static AABB collisionProbe(Entity entity) {
        Vec3 movement = entity.getDeltaMovement();
        if (movement.lengthSqr()
                > MAX_PROBE_MOVEMENT * MAX_PROBE_MOVEMENT) {
            movement = movement.normalize().scale(
                    MAX_PROBE_MOVEMENT);
        }
        return entity.getBoundingBox()
                .expandTowards(movement)
                .inflate(COLLISION_PROBE_MARGIN);
    }

    @Override
    public boolean isPushable() {
        /*
         * Other LivingEntity ticks must not independently select multipart
         * boxes and apply the same separation a second time. The parent runs
         * one anatomical push pass after all boxes have their current pose.
         */
        return false;
    }

    @Override
    public void push(Entity other) {
        if (!this.getParent().canPushFromPart(this, other)
                || this.noPhysics
                || this.isPassengerOfSameVehicle(other)) {
            return;
        }
        double x = other.getX() - this.getX();
        double z = other.getZ() - this.getZ();
        double maximumAxis = Mth.absMax(x, z);
        if (maximumAxis < 0.01F) {
            return;
        }
        maximumAxis = Math.sqrt(maximumAxis);
        x /= maximumAxis;
        z /= maximumAxis;
        double strength = Math.min(1.0, 1.0 / maximumAxis)
                * 0.05F;
        x *= strength;
        z *= strength;
        this.getParent().pushFromPart(-x, 0.0, -z);
        if (!other.isVehicle() && other.isPushable()) {
            other.push(x, 0.0, z);
        }
    }

    /**
     * Multipart entities are not ticked independently, so an impulse applied
     * to a part must move the logical dinosaur rather than accumulating on the
     * transient part entity.
     */
    @Override
    public void push(double x, double y, double z) {
        this.getParent().pushFromPart(x, y, z);
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
