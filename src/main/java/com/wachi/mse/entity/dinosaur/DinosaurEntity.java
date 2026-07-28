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
import com.wachi.mse.entity.dinosaur.config.DinosaurProfile;
import com.wachi.mse.entity.dinosaur.control.DinosaurBodyRotationControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurLookControl;
import com.wachi.mse.entity.dinosaur.control.DinosaurMoveControl;
import com.wachi.mse.entity.dinosaur.goal.DinosaurRandomStrollGoal;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurHitboxController;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPartEntity;
import com.wachi.mse.entity.dinosaur.navigation.DinosaurGroundPathNavigation;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurBalanceController;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurGaitState;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.procedural.DinosaurTerrainSampler;
import com.wachi.mse.registry.MseDinosaurProfiles;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

public abstract class DinosaurEntity
        extends PathfinderMob
        implements GeoEntity, ProceduralDinosaur {
    private static final Identifier PROFILE_STATS_MODIFIER =
            Identifier.fromNamespaceAndPath(
                    "mc_evolved",
                    "profile_stats");
    private static final EntityDataAccessor<Integer> ACTIVE_ATTACK =
            SynchedEntityData.defineId(
                    DinosaurEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_START_TICK =
            SynchedEntityData.defineId(
                    DinosaurEntity.class,
                    EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final DinosaurBalanceController balanceController = new DinosaurBalanceController();
    private DinosaurProfile profile;
    private DinosaurProceduralConfig baseProceduralConfig;
    private DinosaurProceduralConfig scaledProceduralConfig;
    private float cachedProceduralScale = Float.NaN;
    private DinosaurCombatController combatController;
    private DinosaurHitboxController hitboxController;
    private DinosaurProceduralPose cachedAuthoritativePose;
    private int cachedAuthoritativePoseTick = Integer.MIN_VALUE;
    private boolean movementAnimationWalking;

    protected DinosaurEntity(
            EntityType<? extends DinosaurEntity> entityType,
            Level level) {
        super(entityType, level);
        this.loadProfile();
        initCombatController();
        this.hitboxController = new DinosaurHitboxController(
                this,
                this.proceduralConfig().skeleton());
        this.setId(ENTITY_COUNTER.getAndAdd(
                this.hitboxController.parts().length + 1) + 1);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    public void initCombatController(){
        if(combatController == null) this.combatController = new DinosaurCombatController(this);
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
        initCombatController();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (this.usesGenericCombat()) {
            this.goalSelector.addGoal(
                    2,
                    new DinosaurAttackGoal(
                            this,
                            this.combatController,
                            1.0));
        }
        this.goalSelector.addGoal(5, new DinosaurRandomStrollGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.registerCustomGoals();
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

    /**
     * Enables Minecraft's native mounted-sprint path. LocalPlayer already
     * handles the configured sprint key, double-tap window, forward-input
     * requirement and START/STOP_SPRINTING packets when the controlled
     * vehicle reports this capability.
     */
    @Override
    public boolean canSprint() {
        return true;
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
        float maximumSpeed =
                (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        /*
         * Player.isSprinting() is vanilla's authoritative result for both the
         * sprint key and double-tap-forward logic. Map that existing state to
         * the mount instead of introducing another input packet or keybind.
         */
        return controller.isSprinting()
                ? maximumSpeed
                : DinosaurMoveControl.idleSpeedFor(this);
    }

    @Override
    public void tick() {
        super.tick();
        this.cachedAuthoritativePoseTick = Integer.MIN_VALUE;
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            if (this.usesGenericCombat()) {
                this.combatController.tickServer(serverLevel);
            }
            this.tickCustomServer(serverLevel);
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
                this.profile.gait().movementBlendTicks(),
                animationTest -> {
                    DinosaurGaitState gait =
                            DinosaurGaitState.sampleInterpolated(
                                    this,
                                    this.proceduralConfig(),
                                    animationTest.renderState()
                                            .getPartialTick());
                    if (this.movementAnimationWalking) {
                        if (gait.activity() <= 0.02F) {
                            this.movementAnimationWalking = false;
                        }
                    } else if (gait.activity() >= 0.05F) {
                        this.movementAnimationWalking = true;
                    }

                    if (!this.movementAnimationWalking) {
                        animationTest.setControllerSpeed(1.0F);
                        return animationTest.setAndContinue(
                                RawAnimation.begin().thenLoop(
                                        this.profile.idleAnimation()));
                    }

                    PlayState result = animationTest.setAndContinue(
                            RawAnimation.begin().thenLoop(
                                    this.profile.walkAnimation()));
                    // GeckoLib skips bone evaluation when controller speed is
                    // exactly zero. Keep evaluation active, then seek the
                    // timeline to the shared distance-driven phase every
                    // frame so its own clock can never drift from contacts.
                    animationTest.setControllerSpeed(1.0F);
                    animationTest.controller().setAnimationTime(
                            gait.phase()
                                    * this.proceduralConfig()
                                            .gait()
                                            .walkAnimationLengthSeconds());
                    return result;
                }));
        AnimationController<DinosaurEntity> attacks =
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
        this.registerCustomControllers(controllers);
        controllers.add(attacks);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public DinosaurProceduralConfig proceduralConfig() {
        float scale = this.getScale();
        if (this.scaledProceduralConfig == null
                || Float.compare(scale, this.cachedProceduralScale) != 0) {
            this.scaledProceduralConfig =
                    this.baseProceduralConfig.scaled(scale);
            this.cachedProceduralScale = scale;
            this.cachedAuthoritativePoseTick = Integer.MIN_VALUE;
        }
        return this.scaledProceduralConfig;
    }

    public DinosaurProfile dinosaurProfile() {
        return this.profile;
    }

    public final Identifier profileId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(this.getType());
    }

    private void loadProfile() {
        ResourceKey<DinosaurProfile> profileKey = ResourceKey.create(
                MseDinosaurProfiles.REGISTRY,
                this.profileId()
        );
        this.profile = this.level().registryAccess()
                .lookupOrThrow(MseDinosaurProfiles.REGISTRY)
                .getValueOrThrow(profileKey);

        this.baseProceduralConfig =
                this.profile.createBaseConfig();
        this.scaledProceduralConfig = null;
        this.cachedProceduralScale = Float.NaN;
        this.cachedAuthoritativePoseTick = Integer.MIN_VALUE;
        if (!this.level().isClientSide()) {
            this.applyProfileStats();
        }
        this.moveControl = new DinosaurMoveControl(
                this,
                this.baseProceduralConfig.orientation(),
                this.baseProceduralConfig.navigation());
        this.lookControl = new DinosaurLookControl(
                this,
                this.baseProceduralConfig.orientation());
        if (this.navigation instanceof DinosaurGroundPathNavigation dinosaurNavigation) {
            dinosaurNavigation.refreshSpeciesConfig();
        }

        if (this.hitboxController != null) {
            this.hitboxController = new DinosaurHitboxController(
                    this,
                    this.baseProceduralConfig.skeleton());
            this.setId(this.getId());
        }
    }

    private void applyProfileStats() {
        DinosaurProfile.Stats stats = this.profile.stats();
        this.applyProfileStat(
                Attributes.MAX_HEALTH,
                stats.maxHealth(),
                DinosaurProfile.Stats.DEFAULT.maxHealth());
        this.applyProfileStat(
                Attributes.MOVEMENT_SPEED,
                stats.movementSpeed(),
                DinosaurProfile.Stats.DEFAULT.movementSpeed());
        this.applyProfileStat(
                Attributes.ATTACK_DAMAGE,
                stats.attackDamage(),
                DinosaurProfile.Stats.DEFAULT.attackDamage());
        this.applyProfileStat(
                Attributes.FOLLOW_RANGE,
                stats.followRange(),
                DinosaurProfile.Stats.DEFAULT.followRange());
        this.applyProfileStat(
                Attributes.STEP_HEIGHT,
                stats.stepHeight(),
                DinosaurProfile.Stats.DEFAULT.stepHeight());
        this.applyOptionalProfileStat(
                Attributes.CAMERA_DISTANCE,
                stats.ridingCameraDistance());
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applyProfileStat(
            Holder<Attribute> attribute,
            double desired,
            double defaultValue) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(PROFILE_STATS_MODIFIER);
        double difference = desired - defaultValue;
        if (Math.abs(difference) > 1.0E-9) {
            instance.addTransientModifier(new AttributeModifier(
                    PROFILE_STATS_MODIFIER,
                    difference,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void applyOptionalProfileStat(
            Holder<Attribute> attribute,
            Optional<Double> desiredValue) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(PROFILE_STATS_MODIFIER);
        desiredValue.ifPresent(desired -> {
            double difference = desired - instance.getBaseValue();
            if (Math.abs(difference) > 1.0E-9) {
                instance.addTransientModifier(new AttributeModifier(
                        PROFILE_STATS_MODIFIER,
                        difference,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        });
    }

    /**
     * Return false when a Java species completely replaces the generic
     * datapack attack scheduler.
     */
    protected boolean usesGenericCombat() {
        return true;
    }

    /**
     * Adds exceptional AI without copying the common locomotion goals.
     */
    protected void registerCustomGoals() {
    }

    /**
     * Adds Java-only animation controllers for exceptional attacks or states.
     */
    protected void registerCustomControllers(
            AnimatableManager.ControllerRegistrar controllers) {
    }

    /**
     * Server hook for behavior that is too stateful or specific for JSON.
     */
    protected void tickCustomServer(ServerLevel level) {
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
    public PartEntity<?>[] getParts() {
        return this.hitboxController.parts();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
