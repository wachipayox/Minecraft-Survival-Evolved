package com.wachi.mse.entity.dinosaur.combat;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPoseTransforms;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/**
 * Executes data-driven attacks against animated body-part volumes.
 */
public final class DinosaurCombatController {
    private final DinosaurEntity dinosaur;
    private final Set<Integer> hitEntityIds = new HashSet<>();
    private int cooldownTicks;

    public DinosaurCombatController(DinosaurEntity dinosaur) {
        this.dinosaur = dinosaur;
    }

    public boolean canStart(LivingEntity target) {
        if (this.cooldownTicks > 0
                || this.dinosaur.activeAttack() != null
                || this.dinosaur.proceduralConfig()
                        .combat()
                        .attacks()
                        .isEmpty()
                || !target.isAlive()
                || !this.dinosaur.getSensing().hasLineOfSight(target)) {
            return false;
        }
        DinosaurCombatConfig.Attack attack =
                this.dinosaur.proceduralConfig().combat().attacks().getFirst();
        DinosaurProceduralPose pose =
                this.dinosaur.authoritativeProceduralPose();
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                DinosaurPoseTransforms.snapshot(
                        this.dinosaur.proceduralConfig(),
                        pose,
                        attack,
                        attack.activeStartTick());
        AABB targetBounds = target.getBoundingBox();
        double tolerance = Math.max(
                0.25,
                0.2 * this.dinosaur.proceduralConfig().scale());
        for (DinosaurCombatConfig.AttackVolume volume : attack.volumes()) {
            AABB strikeBounds = DinosaurPoseTransforms.attackVolumeBounds(
                    snapshot,
                    volume);
            if (strikeBounds.inflate(tolerance).intersects(targetBounds)) {
                return true;
            }
        }
        return false;
    }

    public boolean start(LivingEntity target) {
        if (!canStart(target)) {
            return false;
        }
        DinosaurCombatConfig.Attack attack =
                this.dinosaur.proceduralConfig().combat().attacks().getFirst();
        this.hitEntityIds.clear();
        this.dinosaur.beginAttack(attack);
        this.dinosaur.getNavigation().stop();
        return true;
    }

    public void tickServer(ServerLevel level) {
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
        }
        DinosaurCombatConfig.Attack attack = this.dinosaur.activeAttack();
        if (attack == null) {
            return;
        }

        int elapsed = (int) this.dinosaur.attackElapsedTicks(0.0F);
        if (attack.activeAt(elapsed)) {
            applyAttackVolumes(level, attack, elapsed);
        }
        if (elapsed >= attack.durationTicks()) {
            this.cooldownTicks = Math.max(
                    this.cooldownTicks,
                    attack.cooldownTicks() - attack.durationTicks());
            this.dinosaur.finishAttack();
            this.hitEntityIds.clear();
        }
    }

    private void applyAttackVolumes(
            ServerLevel level,
            DinosaurCombatConfig.Attack attack,
            int elapsedTicks) {
        DinosaurProceduralPose pose =
                this.dinosaur.authoritativeProceduralPose();
        DinosaurPoseTransforms.PoseSnapshot snapshot =
                DinosaurPoseTransforms.snapshot(
                        this.dinosaur.proceduralConfig(),
                        pose,
                        attack,
                        elapsedTicks);
        LivingEntity primary = this.dinosaur.getTarget();
        for (DinosaurCombatConfig.AttackVolume volume : attack.volumes()) {
            AABB bounds = DinosaurPoseTransforms.attackVolumeBounds(
                    snapshot,
                    volume);
            if (attack.hitMode() == DinosaurCombatConfig.HitMode.PRIMARY_TARGET) {
                if (primary != null && bounds.intersects(primary.getBoundingBox())) {
                    hurt(level, primary, attack);
                }
                continue;
            }
            for (LivingEntity target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    bounds,
                    this::canHit)) {
                hurt(level, target, attack);
            }
        }
    }

    private boolean canHit(LivingEntity target) {
        return target != this.dinosaur
                && target.isAlive()
                && !target.isPassengerOfSameVehicle(this.dinosaur)
                && !this.dinosaur.isAlliedTo(target)
                && this.dinosaur.getSensing().hasLineOfSight(target);
    }

    private void hurt(
            ServerLevel level,
            LivingEntity target,
            DinosaurCombatConfig.Attack attack) {
        if (!canHit(target) || !this.hitEntityIds.add(target.getId())) {
            return;
        }
        DamageSource source = this.dinosaur.damageSources()
                .mobAttack(this.dinosaur);
        float damage = (float) (
                this.dinosaur.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        * attack.damageMultiplier());
        if (target.hurtServer(level, source, damage)) {
            double x = target.getX() - this.dinosaur.getX();
            double z = target.getZ() - this.dinosaur.getZ();
            target.knockback(attack.knockback(), x, z);
            this.dinosaur.setLastHurtMob(target);
        }
    }
}
