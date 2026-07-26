package com.wachi.mse.entity.dinosaur;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PrototypeDinosaurEntity
        extends PathfinderMob
        implements GeoEntity, ProceduralDinosaur {
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.idle");
    private static final RawAnimation WALK_ANIMATION =
            RawAnimation.begin().thenLoop("animation.prototype_dinosaur.walk");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public PrototypeDinosaurEntity(EntityType<? extends PrototypeDinosaurEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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
