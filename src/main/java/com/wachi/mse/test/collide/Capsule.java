package com.wachi.mse.test.collide;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record Capsule(
        Vec3 start,
        Vec3 end,
        double radius
) implements Collider {
    public AABB bounds() {
        return new AABB(
                Math.min(start.x, end.x) - radius,
                Math.min(start.y, end.y) - radius,
                Math.min(start.z, end.z) - radius,
                Math.max(start.x, end.x) + radius,
                Math.max(start.y, end.y) + radius,
                Math.max(start.z, end.z) + radius
        );
    }

    @Override
    public boolean collidesWith(AABB aabb) {
        if(Collider.super.collidesWith(aabb)) return true;

        var expanded = aabb.inflate(radius());
        return expanded.clip(start(), end()).isPresent()
                || expanded.contains(start())
                || expanded.contains(end());
    }

    public ClipContext getBlockClipContext(Entity entity) {
        return getClipContext(entity, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
    }

    public ClipContext getClipContext(Entity entity, ClipContext.Block block, ClipContext.Fluid fluid) {
        return new ClipContext(
                start(), end(), block, fluid, entity
        );
    }
}
