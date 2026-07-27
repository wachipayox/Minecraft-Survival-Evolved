package com.wachi.mse.entity.dinosaur.combat;

import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Pursues at the requested full locomotion speed and delegates damage to
 * species attack volumes instead of vanilla centre-to-centre melee reach.
 */
public final class DinosaurAttackGoal extends Goal {
    private final PrototypeDinosaurEntity dinosaur;
    private final DinosaurCombatController combat;
    private final double speedModifier;
    private int repathTicks;

    public DinosaurAttackGoal(
            PrototypeDinosaurEntity dinosaur,
            DinosaurCombatController combat,
            double speedModifier) {
        this.dinosaur = dinosaur;
        this.combat = combat;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.dinosaur.getTarget();
        return validTarget(target);
    }

    @Override
    public boolean canContinueToUse() {
        return validTarget(this.dinosaur.getTarget());
    }

    @Override
    public void start() {
        this.dinosaur.setAggressive(true);
        this.repathTicks = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.dinosaur.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.dinosaur.setTarget(null);
        }
        this.dinosaur.setAggressive(false);
        this.dinosaur.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.dinosaur.getTarget();
        if (!validTarget(target)) {
            return;
        }
        this.dinosaur.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.dinosaur.activeAttack() != null) {
            return;
        }
        if (this.combat.start(target)) {
            return;
        }
        if (--this.repathTicks <= 0) {
            this.dinosaur.getNavigation().moveTo(target, this.speedModifier);
            this.repathTicks = this.adjustedTickDelay(5);
        }
    }

    private static boolean validTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && (!(target instanceof Player player)
                        || (!player.isCreative() && !player.isSpectator()));
    }
}
