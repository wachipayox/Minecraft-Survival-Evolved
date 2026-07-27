package com.wachi.mse.entity.dinosaur.hitbox;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Updates a bounded number of logical body parts from the authoritative pose.
 *
 * <p>Idle parts update every second tick, staggered by entity ID. Active
 * attacks update every tick because their volumes move quickly.</p>
 */
public final class DinosaurHitboxController {
    private static final int IDLE_UPDATE_INTERVAL_TICKS = 2;

    private final PrototypeDinosaurEntity parent;
    private final DinosaurPartEntity[] parts;
    private AABB visualBounds;

    public DinosaurHitboxController(
            PrototypeDinosaurEntity parent,
            DinosaurSkeletonConfig skeleton) {
        this.parent = parent;
        this.parts = new DinosaurPartEntity[skeleton.hitboxParts().size()];
        for (int index = 0; index < this.parts.length; index++) {
            this.parts[index] = new DinosaurPartEntity(
                    parent,
                    skeleton.hitboxParts().get(index));
        }
        this.visualBounds = parent.getBoundingBox();
    }

    public DinosaurPartEntity[] parts() {
        return this.parts;
    }

    public AABB visualBounds() {
        return this.visualBounds;
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
        Vec3 end = start.add(player.getLookAngle().scale(8.0));
        return DinosaurPoseTransforms.hitboxPartBoxBounds(
                        poseSnapshot(),
                        part)
                .stream()
                .anyMatch(box -> box.clip(start, end).isPresent());
    }

    public void tick() {
        boolean attacking = this.parent.activeAttack() != null;
        if (!attacking
                && (this.parent.tickCount + this.parent.getId())
                        % IDLE_UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        DinosaurProceduralConfig config = this.parent.proceduralConfig();
        DinosaurProceduralPose pose =
                this.parent.authoritativeProceduralPose();
        DinosaurCombatConfig.Attack attack = this.parent.activeAttack();
        float elapsed = this.parent.attackElapsedTicks(0.0F);
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                DinosaurPoseTransforms.snapshot(
                        config,
                        pose,
                        attack,
                        elapsed);
        AABB union = this.parent.getBoundingBox();
        for (DinosaurPartEntity part : this.parts) {
            AABB partBounds = DinosaurPoseTransforms.hitboxPartBounds(
                    snapshot,
                    part.partConfig());
            part.setPartBounds(partBounds);
            union = union.minmax(partBounds);
        }
        this.visualBounds = union;
    }

    private DinosaurPoseTransforms.PoseSnapshot poseSnapshot() {
        return DinosaurPoseTransforms.snapshot(
                this.parent.proceduralConfig(),
                this.parent.authoritativeProceduralPose(),
                this.parent.activeAttack(),
                this.parent.attackElapsedTicks(0.0F));
    }
}
