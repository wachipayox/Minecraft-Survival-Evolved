package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Updates a bounded number of logical body parts from the authoritative pose.
 *
 * <p>Parts update every tick because they are physical push/collision volumes,
 * not only occasional combat broad-phase entries.</p>
 */
public final class DinosaurHitboxController {
    private static final int IDLE_UPDATE_INTERVAL_TICKS = 2;
    private static final int SUPPORTED_ENTITY_GRACE_TICKS = 4;
    private static final double SUPPORT_BELOW_TOLERANCE = 0.08;
    private static final double PLAYER_RAY_TOLERANCE_BLOCKS = 0.10;

    private final DinosaurEntity parent;
    private final DinosaurPartEntity[] subParts;
    private final DinosaurTransportController transportController;
    private AABB visualBounds;
    private DinosaurPoseTransforms.PoseSnapshot appliedPose;
    private int continuousUpdateUntilTick = Integer.MIN_VALUE;

    public DinosaurHitboxController(
            DinosaurEntity parent,
            DinosaurSkeletonConfig skeleton
    ) {
        this.parent = parent;
        List<DinosaurPartEntity> physicalParts = new ArrayList<>();
        for (DinosaurSkeletonConfig.HitboxPart logicalPart
                : skeleton.hitboxParts()) {
            for (DinosaurSkeletonConfig.BoneBox box
                    : logicalPart.boxes()) {
                physicalParts.add(new DinosaurPartEntity(
                        parent,
                        logicalPart,
                        box,
                        physicalParts.size()));
            }
        }
        this.subParts = physicalParts.toArray(
                DinosaurPartEntity[]::new);
        this.transportController =
                new DinosaurTransportController(parent);
        this.visualBounds = parent.getBoundingBox();
    }

    public DinosaurPartEntity[] parts() {
        return this.subParts;
    }

    public AABB visualBounds() {
        return this.visualBounds;
    }

    public boolean isTransportSupported(Entity entity) {
        return this.transportController.isSupported(entity);
    }

    public boolean isActivelyTransporting(Entity entity) {
        return this.transportController.isActivelyTransporting(
                entity);
    }

    public boolean preciselySupportsAny(Entity entity) {
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                this.appliedPose != null
                        ? this.appliedPose
                        : poseSnapshot();
        AABB entityBounds = entity.getBoundingBox();
        for (DinosaurPartEntity part : this.subParts) {
            if (DinosaurPoseTransforms.hitboxBoxSupportPoint(
                            snapshot,
                            part.boxConfig(),
                            entityBounds,
                            SUPPORT_BELOW_TOLERANCE,
                            SUPPORT_BELOW_TOLERANCE)
                    != null) {
                return true;
            }
        }
        return false;
    }

    public boolean preciselySupports(
            DinosaurPartEntity part,
            AABB entityBounds) {
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                this.appliedPose != null
                        ? this.appliedPose
                        : poseSnapshot();
        return DinosaurPoseTransforms.hitboxBoxSupportPoint(
                        snapshot,
                        part.boxConfig(),
                        entityBounds,
                        SUPPORT_BELOW_TOLERANCE,
                        0.04)
                != null;
    }

    public boolean preciselyIntersects(
            DinosaurPartEntity part,
            AABB bounds) {
        return DinosaurPoseTransforms.hitboxBoxIntersectsAabb(
                poseSnapshot(),
                part.boxConfig(),
                bounds);
    }

    public Vec3 preciseTopSurface(
            DinosaurPartEntity part,
            AABB entityBounds) {
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                this.appliedPose != null
                        ? this.appliedPose
                        : poseSnapshot();
        return DinosaurPoseTransforms.hitboxBoxTopSurfacePoint(
                snapshot,
                part.boxConfig(),
                entityBounds);
    }

    /**
     * The native part AABB is only the broad phase. Player rays and projectile
     * sweeps must still touch one of the smaller transformed bone boxes.
     */
    public boolean isPreciseHit(
            DinosaurSkeletonConfig.HitboxPart part,
            DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) {
            return rayTouchesPart(part, player);
        }
        List<AABB> boxes = DinosaurPoseTransforms.hitboxPartBoxBounds(
                poseSnapshot(),
                part);
        if (direct != null && direct != source.getEntity()) {
            Vec3 movement = direct.getDeltaMovement();
            Vec3 start = direct.position().subtract(movement);
            Vec3 end = direct.position().add(movement);
            return boxes.stream().anyMatch(box ->
                    box.intersects(direct.getBoundingBox())
                            || box.inflate(
                                            direct.getBbWidth() * 0.5,
                                            direct.getBbHeight() * 0.5,
                                            direct.getBbWidth() * 0.5)
                                    .clip(start, end)
                                    .isPresent());
        }
        // Explosions, commands and environmental sources do not carry a
        // meaningful selection ray and intentionally retain regional damage.
        return true;
    }

    public boolean rayTouchesPart(
            DinosaurSkeletonConfig.HitboxPart part,
            Player player) {
        Vec3 start = player.getEyePosition();
        double scale = this.parent.proceduralConfig().scale();
        double tolerance =
                PLAYER_RAY_TOLERANCE_BLOCKS * scale;
        double reach = player.entityInteractionRange() + tolerance;
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        return DinosaurPoseTransforms.hitboxPartBoxBounds(
                        poseSnapshot(),
                        part)
                .stream()
                /*
                 * Client selection and the authoritative pose can be one
                 * movement/animation tick apart. A small scale-aware margin
                 * prevents valid multipart hits from being rejected while
                 * preserving the per-bone test instead of accepting the whole
                 * broad-phase union.
                 */
                .anyMatch(box -> box.inflate(tolerance)
                        .clip(start, end)
                        .isPresent());
    }

    public void tick() {
        if (!this.parent.level().isClientSide()
                && this.parent.tickCount % 20 == 0) {
            long gameTime = this.parent.level().getGameTime();
            for (DinosaurPartEntity part : this.subParts) {
                part.purgeExpiredSupportVetoes(gameTime);
            }
        }
        List<DinosaurTransportController.SupportContact> supportContacts =
                this.appliedPose == null
                        ? List.of()
                        : this.transportController.findContacts(
                                this.subParts,
                                this.appliedPose,
                                this.visualBounds);
        if (!supportContacts.isEmpty()) {
            this.continuousUpdateUntilTick =
                    this.parent.tickCount + SUPPORTED_ENTITY_GRACE_TICKS;
        }
        boolean requiresContinuousUpdates =
                this.parent.activeAttack() != null
                        || this.parent.tickCount
                                <= this.continuousUpdateUntilTick;
        boolean periodicUpdate =
                (this.parent.tickCount + this.parent.getId())
                        % IDLE_UPDATE_INTERVAL_TICKS == 0;
        if (!requiresContinuousUpdates && !periodicUpdate) {
            return;
        }

        DinosaurProceduralConfig config = this.parent.proceduralConfig();
        DinosaurProceduralPose pose = this.parent.authoritativeProceduralPose();
        DinosaurCombatConfig.Attack attack = this.parent.activeAttack();

        float elapsed = this.parent.attackElapsedTicks(0.0F);

        DinosaurPoseTransforms.PoseSnapshot snapshot =
                DinosaurPoseTransforms.snapshot(
                        config,
                        pose,
                        attack,
                        elapsed
                );

        AABB union = this.parent.getBoundingBox();
        for (DinosaurPartEntity part : this.subParts) {
            AABB bounds = DinosaurPoseTransforms.hitboxBoxBounds(
                    snapshot,
                    part.boxConfig());
            part.setPartBounds(bounds);
            union = union.minmax(bounds);
        }
        this.visualBounds = union;
        if (this.appliedPose != null && !supportContacts.isEmpty()) {
            this.transportController.transport(
                    supportContacts,
                    this.appliedPose,
                    snapshot);
        }
        this.appliedPose = snapshot;
        if (!this.parent.level().isClientSide()) {
            this.resolveEmbeddedPlayers();
        }
    }

    private void resolveEmbeddedPlayers() {
        for (Entity candidate : this.parent.level().getEntities(
                this.parent,
                this.visualBounds,
                entity -> entity instanceof ServerPlayer)) {
            ServerPlayer player = (ServerPlayer) candidate;
            for (DinosaurPartEntity part : this.subParts) {
                if (part.pushAboveIfEmbedded(player)) {
                    break;
                }
            }
        }
    }

    private DinosaurPoseTransforms.PoseSnapshot poseSnapshot() {
        return DinosaurPoseTransforms.snapshot(
                this.parent.proceduralConfig(),
                this.parent.authoritativeProceduralPose(),
                this.parent.activeAttack(),
                this.parent.attackElapsedTicks(0.0F));
    }
}
