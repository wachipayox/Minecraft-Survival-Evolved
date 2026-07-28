package com.wachi.mse.entity.dinosaur.goal;

import com.wachi.mse.entity.dinosaur.ProceduralDinosaur;
import com.wachi.mse.entity.dinosaur.control.DinosaurMoveControl;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Low-cost idle wandering biased toward the dinosaur's current heading.
 *
 * <p>The destination radius is derived from stride length, so scaled animals
 * choose proportionally longer trips without adding path searches. Minecraft's
 * random-position helper evaluates candidate blocks; path creation still
 * happens only once for the selected destination.</p>
 */
public final class DinosaurRandomStrollGoal extends RandomStrollGoal {
    private static final int DEFAULT_INTERVAL_TICKS = 120;
    private static final int MIN_HORIZONTAL_RANGE = 10;
    private static final int VERTICAL_RANGE = 7;
    private static final double STRIDES_PER_DESTINATION = 3.0;
    private static final double FORWARD_CONE_RADIANS =
            Math.toRadians(22.5);

    private final PathfinderMob dinosaur;

    public DinosaurRandomStrollGoal(PathfinderMob dinosaur) {
        super(
                dinosaur,
                1.0,
                DEFAULT_INTERVAL_TICKS);
        this.dinosaur = dinosaur;
    }

    @Override
    public boolean canUse() {
        return !this.dinosaur.isVehicle()
                && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.dinosaur.isVehicle()
                && super.canContinueToUse();
    }

    @Override
    public void start() {
        double idleSpeedModifier =
                DinosaurMoveControl.idleSpeedModifierFor(this.dinosaur);
        if (this.dinosaur.getMoveControl()
                instanceof DinosaurMoveControl moveControl) {
            moveControl.beginIdleMovement();
        }
        this.dinosaur.getNavigation().moveTo(
                this.wantedX,
                this.wantedY,
                this.wantedZ,
                idleSpeedModifier);
    }

    @Override
    public void stop() {
        if (this.dinosaur.getMoveControl()
                instanceof DinosaurMoveControl moveControl) {
            moveControl.endIdleMovement();
        }
        super.stop();
    }

    @Override
    protected Vec3 getPosition() {
        ProceduralDinosaur procedural = (ProceduralDinosaur) this.dinosaur;
        int horizontalRange = Math.max(
                MIN_HORIZONTAL_RANGE,
                (int) Math.ceil(
                        procedural.proceduralConfig()
                                .gait()
                                .strideLengthBlocks()
                                * STRIDES_PER_DESTINATION));

        double yawRadians = Math.toRadians(this.dinosaur.getYRot());
        Vec3 forward = new Vec3(
                -Math.sin(yawRadians),
                0.0,
                Math.cos(yawRadians));
        Vec3 focus = this.dinosaur.position()
                .add(forward.scale(horizontalRange));
        /*
         * Do not fall back to an unrestricted random position. Failing this
         * cheap forward-cone sample merely skips the current stroll, avoiding
         * a path search and an agitated turn toward a point behind the animal.
         */
        return DefaultRandomPos.getPosTowards(
                this.dinosaur,
                horizontalRange,
                VERTICAL_RANGE,
                focus,
                FORWARD_CONE_RADIANS);
    }
}
