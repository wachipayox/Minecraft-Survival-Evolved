package com.wachi.mse.mixin.world;

import com.wachi.mse.test.collide.terrain.TerrainChangeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow
    @Final
    private Level level;

    @Inject(
            method =
                    "setBlockState(" +
                            "Lnet/minecraft/core/BlockPos;" +
                            "Lnet/minecraft/world/level/block/state/BlockState;" +
                            "I)" +
                            "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void mse$onBlockStateChanged(
            BlockPos pos,
            BlockState newState,
            int flags,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if(level == null) return;

        BlockState oldState = cir.getReturnValue();

        if (oldState == null) {
            return;
        }

        TerrainChangeTracker.markChanged(
                level,
                pos.asLong()
        );
    }
}