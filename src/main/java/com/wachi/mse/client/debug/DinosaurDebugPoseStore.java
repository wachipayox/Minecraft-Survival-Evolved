package com.wachi.mse.client.debug;

import com.wachi.mse.entity.dinosaur.procedural.DinosaurProceduralPose;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class DinosaurDebugPoseStore {
    private static final Map<LivingEntity, DinosaurProceduralPose> POSES =
            new WeakHashMap<>();

    private DinosaurDebugPoseStore() {
    }

    public static void update(
            LivingEntity entity,
            DinosaurProceduralPose pose) {
        POSES.put(entity, pose);
    }

    public static @Nullable DinosaurProceduralPose get(LivingEntity entity) {
        return POSES.get(entity);
    }

    public static void clear() {
        POSES.clear();
    }
}
