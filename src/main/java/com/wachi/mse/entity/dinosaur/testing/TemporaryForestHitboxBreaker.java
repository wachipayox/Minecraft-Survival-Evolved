package com.wachi.mse.entity.dinosaur.testing;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * TEMPORARY TEST CODE: removes forest blocks touched by dinosaur subhitboxes.
 *
 * <p>Delete this class and its single call from {@code DinosaurEntity#tick}
 * after giant-dinosaur transport testing.</p>
 */
public final class TemporaryForestHitboxBreaker {
    private static final double EDGE_EPSILON = 1.0E-7;
    private static final double TEST_BREAK_INFLATION = 2.0;

    private TemporaryForestHitboxBreaker() {
    }

    public static void tick(
            DinosaurEntity dinosaur,
            DinosaurPartEntity[] parts) {
        if (!(dinosaur.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        for (DinosaurPartEntity part : parts) {
            /*
             * Test-only broad destruction volume. AABB#inflate returns a
             * larger copy, so the real collision box remains untouched.
             */
            AABB bounds = part.getBoundingBox().inflate(
                    TEST_BREAK_INFLATION);
            int minX = Mth.floor(bounds.minX + EDGE_EPSILON);
            int minY = Mth.floor(bounds.minY + EDGE_EPSILON);
            int minZ = Mth.floor(bounds.minZ + EDGE_EPSILON);
            int maxX = Mth.floor(bounds.maxX - EDGE_EPSILON);
            int maxY = Mth.floor(bounds.maxY - EDGE_EPSILON);
            int maxZ = Mth.floor(bounds.maxZ - EDGE_EPSILON);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        if (state.is(BlockTags.LOGS)
                                || state.is(BlockTags.LEAVES)) {
                            level.destroyBlock(
                                    cursor.immutable(),
                                    false,
                                    dinosaur);
                        }
                    }
                }
            }
        }
    }
}
