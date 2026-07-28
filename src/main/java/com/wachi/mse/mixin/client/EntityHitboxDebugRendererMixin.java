package com.wachi.mse.mixin.client;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {

    private static final int MULTIPART_HITBOX_COLOR = 0xFF00FF00;

    @Inject(
            method = "showHitboxes",
            at = @At("TAIL")
    )
    private void mse$renderMultipartHitboxes(
            Entity entity,
            float partialTick,
            boolean serverEntity,
            CallbackInfo ci
    ) {
        /*
         * Dibujamos únicamente la copia cliente para evitar que en singleplayer
         * se superpongan la versión cliente y la versión del servidor integrado.
         */
        if (serverEntity) {
            return;
        }

        /*
         * El Ender Dragon ya se dibuja mediante el tratamiento especial vanilla.
         */
        if (entity instanceof EnderDragon || !entity.isMultipartEntity()) {
            return;
        }

        PartEntity<?>[] parts = entity.getParts();

        if (parts == null || parts.length == 0) {
            return;
        }

        GizmoStyle style = GizmoStyle.stroke(
                MULTIPART_HITBOX_COLOR,
                2.0F
        );

        for (PartEntity<?> part : parts) {
            if (part == null || part.isRemoved()) {
                continue;
            }

            var interpolatedPosition = part.getPosition(partialTick);
            var logicalPosition = part.position();
            var delta = interpolatedPosition.subtract(logicalPosition);

            AABB interpolatedBox = part.getBoundingBox().move(
                    delta.x,
                    delta.y,
                    delta.z
            );

            Gizmos.cuboid(interpolatedBox, style, true);
        }
    }
}