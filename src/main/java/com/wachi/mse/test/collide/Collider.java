package com.wachi.mse.test.collide;

import net.minecraft.world.phys.AABB;

import java.util.List;

public interface Collider {

    AABB bounds();

    default boolean collidesWith(AABB aabb) {
        return bounds().intersects(aabb);
    }

    default boolean collidesWith(List<AABB> aabbs) {
        return aabbs.stream().anyMatch(this::collidesWith);
    }
}
