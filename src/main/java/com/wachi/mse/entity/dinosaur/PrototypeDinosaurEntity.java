package com.wachi.mse.entity.dinosaur;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.wachi.mse.entity.dinosaur.combat.DinosaurAttackGoal;
import com.wachi.mse.entity.dinosaur.combat.DinosaurCombatController;
import com.wachi.mse.entity.dinosaur.config.DinosaurCombatConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import com.wachi.mse.entity.dinosaur.config.DinosaurSkeletonConfig;
import com.wachi.mse.entity.dinosaur.control.DinosaurBodyRotationControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurLookControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurMoveControl;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurHitboxController;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPartEntity;
import com.wachi.mse.entity.dinosaur.navigation.DinosaurGroundPathNavigation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBalanceController;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSampler;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PrototypeDinosaurEntity
        extends PathfinderMob
        implements GeoEntity, ProceduralDinosaur {
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.idle");
    private static final RawAnimation WALK_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.walk");
    private static final EntityDataAccessor<Integer> ACTIVE_ATTACK =
            SynchedEntityData.defineId(
                    PrototypeDinosaurEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_START_TICK =
            SynchedEntityData.defineId(
                    PrototypeDinosaurEntity.class,
                    EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final DinosaurBalanceController balanceController =
            new DinosaurBalanceController();
    private final DinosaurCombatController combatController;
    private final DinosaurHitboxController hitboxController;
    private DinosaurProceduralPose cachedAuthoritativePose;
    private int cachedAuthoritativePoseTick = Integer.MIN_VALUE;

    public PrototypeDinosaurEntity(EntityType<? extends PrototypeDinosaurEntity> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new DinosaurMoveControl(
                this,
                this.proceduralConfig().orientation(),
                this.proceduralConfig().navigation());
        this.lookControl = new DinosaurLookControl(
                this,
                this.proceduralConfig().orientation());
        this.combatController = new DinosaurCombatController(this);
        this.hitboxController = new DinosaurHitboxController(
                this,
                this.proceduralConfig().skeleton());
        this.setId(ENTITY_COUNTER.getAndAdd(
                this.hitboxController.parts().length + 1) + 1);
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
        this.goalSelector.addGoal(
                2,
                new DinosaurAttackGoal(this, this.combatController, 1.0));
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTIVE_ATTACK, 0);
        builder.define(ATTACK_START_TICK, 0);
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
            dinosaurMove.steerRiddenToward(this.yHeadRot);
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
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void tick() {
        super.tick();
        this.cachedAuthoritativePoseTick = Integer.MIN_VALUE;
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            this.combatController.tickServer(serverLevel);
        }
        if (this.isNoAi()) {
            this.balanceController.reset();
        } else {
            // Run after travel on its vanilla movement owner: the server for
            // autonomous mobs and the rider's local client for a mount.
            this.balanceController.tick(this, this.proceduralConfig());
        }
        this.hitboxController.tick();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                "movement",
                5,
                animationTest -> animationTest.setAndContinue(
                        animationTest.isMoving() ? WALK_ANIMATION : IDLE_ANIMATION)));
        AnimationController<PrototypeDinosaurEntity> attacks =
                new AnimationController<>(
                        "attack",
                        0,
                        animationState -> PlayState.STOP);
        for (DinosaurCombatConfig.Attack attack
                : this.proceduralConfig().combat().attacks()) {
            attacks.triggerableAnim(
                    attack.id(),
                    RawAnimation.begin().thenPlay(attack.animationName()));
        }
        controllers.add(attacks);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public DinosaurProceduralConfig proceduralConfig() {
        return DinosaurProceduralConfig.PROTOTYPE;
    }

    @Override
    public DinosaurProceduralPose authoritativeProceduralPose() {
        if (this.cachedAuthoritativePose == null
                || this.cachedAuthoritativePoseTick != this.tickCount) {
            this.cachedAuthoritativePose =
                    DinosaurTerrainSampler.sampleAuthoritative(
                            this,
                            this.proceduralConfig());
            this.cachedAuthoritativePoseTick = this.tickCount;
        }
        return this.cachedAuthoritativePose;
    }

    public DinosaurCombatConfig.Attack activeAttack() {
        return this.proceduralConfig().combat().attack(
                this.entityData.get(ACTIVE_ATTACK));
    }

    public float attackElapsedTicks(float partialTick) {
        if (this.activeAttack() == null) {
            return 0.0F;
        }
        return Math.max(
                0.0F,
                this.tickCount
                        - this.entityData.get(ATTACK_START_TICK)
                        + partialTick);
    }

    public void beginAttack(DinosaurCombatConfig.Attack attack) {
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(
                ACTIVE_ATTACK,
                this.proceduralConfig().combat().syncedIndex(attack));
        this.entityData.set(ATTACK_START_TICK, this.tickCount);
        this.triggerAnim("attack", attack.id());
    }

    public void finishAttack() {
        if (!this.level().isClientSide()) {
            this.entityData.set(ACTIVE_ATTACK, 0);
        }
    }

    public boolean hurtFromPart(
            ServerLevel level,
            DinosaurSkeletonConfig.HitboxPart part,
            DamageSource source,
            float damage) {
        if (!this.hitboxController.isPreciseHit(part, source)) {
            return false;
        }
        return super.hurtServer(
                level,
                source,
                damage * part.incomingDamageMultiplier());
    }

    public boolean canInteractWithPart(
            DinosaurSkeletonConfig.HitboxPart part,
            Player player) {
        return this.hitboxController.rayTouchesPart(part, player);
    }

    public AABB dinosaurVisualBounds() {
        return this.hitboxController.visualBounds();
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.hitboxController == null) {
            return;
        }
        DinosaurPartEntity[] parts = this.hitboxController.parts();
        for (int index = 0; index < parts.length; index++) {
            parts[index].setId(id + index + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return this.hitboxController.parts();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
