package com.wachi.mse.entity.dinosaur.control;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.config.DinosaurOrientationConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * Client-side body presentation that deliberately omits vanilla's stationary
 * "body catches up with head" pivot.
 */
public final class DinosaurBodyRotationControl extends BodyRotationControl {
    private final Mob mob;

    public DinosaurBodyRotationControl(Mob mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void clientTick() {
        DinosaurOrientationConfig config =
                ((ProceduralDinosaur) this.mob).proceduralConfig().orientation();
        double horizontalDistanceSquared =
                Mth.square(this.mob.getX() - this.mob.xo)
                        + Mth.square(this.mob.getZ() - this.mob.zo);
        if (horizontalDistanceSquared
                >= Mth.square(config.minimumTurningDistance())) {
            this.mob.yBodyRot = this.mob.getYRot();
        }
        this.mob.yHeadRot = Mth.rotateIfNecessary(
                this.mob.yHeadRot,
                this.mob.yBodyRot,
                config.maxNeckYawDegrees());
    }
}
