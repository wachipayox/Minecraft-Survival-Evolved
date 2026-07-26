package com.wachi.mse.client.debug;

import com.wachi.mse.client.animation.DinosaurProceduralPose;
import com.wachi.mse.entity.dinosaur.PrototypeDinosaurEntity;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

public final class DinosaurDebugPoseStore {
    private static final Map<PrototypeDinosaurEntity, DinosaurProceduralPose> POSES =
            new WeakHashMap<>();

    private DinosaurDebugPoseStore() {
    }

    public static void update(
            PrototypeDinosaurEntity entity,
            DinosaurProceduralPose pose) {
        POSES.put(entity, pose);
    }

    public static @Nullable DinosaurProceduralPose get(PrototypeDinosaurEntity entity) {
        return POSES.get(entity);
    }

    public static void clear() {
        POSES.clear();
    }
}
