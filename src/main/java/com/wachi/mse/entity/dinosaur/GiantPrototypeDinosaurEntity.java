package com.wachi.mse.entity.dinosaur;

import com.wachi.mse.entity.dinosaur.config.DinosaurProceduralConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Ten-times-scale test fixture for validating that procedural dinosaur
 * systems depend on species geometry instead of prototype-sized constants.
 *
 * <p>The entity deliberately inherits behavior, model resources and
 * animations from {@link PrototypeDinosaurEntity}. Only its physical scale
 * and the matching geometrically scaled procedural configuration differ.</p>
 */
public final class GiantPrototypeDinosaurEntity extends PrototypeDinosaurEntity {
    public static final float TEST_SCALE = 10.0F;

    public GiantPrototypeDinosaurEntity(
            EntityType<? extends GiantPrototypeDinosaurEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createGiantAttributes() {
        return PrototypeDinosaurEntity.createAttributes()
                .add(Attributes.SCALE, TEST_SCALE)
                .add(Attributes.STEP_HEIGHT, 0.6 * TEST_SCALE);
    }

    @Override
    public DinosaurProceduralConfig proceduralConfig() {
        return DinosaurProceduralConfig.GIANT_PROTOTYPE;
    }
}
