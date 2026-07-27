package com.wachi.mse.entity.dinosaur;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.control.DinosaurBodyRotationControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurLookControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurMoveControl;
import com.wachi.mse.entity.dinosaur.navigation.DinosaurGroundPathNavigation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBalanceController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PrototypeDinosaurEntity
        extends PathfinderMob
        implements GeoEntity, ProceduralDinosaur {
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.idle");
    private static final RawAnimation WALK_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.walk");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final DinosaurBalanceController balanceController =
            new DinosaurBalanceController();

    public PrototypeDinosaurEntity(EntityType<? extends PrototypeDinosaurEntity> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new DinosaurMoveControl(
                this,
                this.proceduralConfig().orientation());
        this.lookControl = new DinosaurLookControl(
                this,
                this.proceduralConfig().orientation());
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new DinosaurBodyRotationControl(this);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new DinosaurGroundPathNavigation(this, level);
    }

    @Override
    public int getMaxHeadXRot() {
        return Math.round(Math.max(
                this.proceduralConfig().orientation().maxPitchUpDegrees(),
                this.proceduralConfig().orientation().maxPitchDownDegrees()));
    }

    @Override
    public int getMaxHeadYRot() {
        return Math.round(this.proceduralConfig().orientation().maxNeckYawDegrees());
    }

    @Override
    public int getHeadRotSpeed() {
        return Math.round(this.proceduralConfig().orientation().headYawSpeedDegreesPerTick());
    }

    @Override
    public InteractionResult mobInteract(
            Player player,
            InteractionHand hand) {
        InteractionResult inherited = super.mobInteract(player, hand);
        if (inherited.consumesAction()) {
            return inherited;
        }
        if (this.isVehicle() || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            this.getNavigation().stop();
            this.setTarget(null);
            this.setAggressive(false);
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player
                ? player
                : null;
    }

    @Override
    protected void tickRidden(
            Player controller,
            Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        if (this.lookControl instanceof DinosaurLookControl dinosaurLook) {
            dinosaurLook.tickRidden(controller);
        }
        if (Math.abs(riddenInput.z) > 1.0E-4
                && this.moveControl instanceof DinosaurMoveControl dinosaurMove) {
            dinosaurMove.steerRiddenToward(controller.getYRot());
        }
    }

    @Override
    protected Vec3 getRiddenInput(
            Player controller,
            Vec3 selfInput) {
        float forward = controller.zza;
        if (forward < 0.0F) {
            forward *= 0.25F;
        }
        // Dinosaurs steer in arcs; rider strafe input never becomes lateral
        // translation.
        return new Vec3(0.0, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        float headingMultiplier =
                this.moveControl instanceof DinosaurMoveControl dinosaurMove
                        ? dinosaurMove.speedMultiplierForHeading(
                                controller.getYRot())
                        : 1.0F;
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * headingMultiplier;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.balanceController.tick(this, this.proceduralConfig());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.isNoAi()) {
            this.balanceController.reset();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                "movement",
                5,
                animationTest -> animationTest.setAndContinue(
                        animationTest.isMoving() ? WALK_ANIMATION : IDLE_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public DinosaurProceduralConfig proceduralConfig() {
        return DinosaurProceduralConfig.PROTOTYPE;
    }
}
