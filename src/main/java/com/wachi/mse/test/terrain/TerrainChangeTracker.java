package com.wachi.mse.test.terrain;

import com.wachi.mse.MseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.*;

@EventBusSubscriber
public class TerrainChangeTracker {

    private static final Map<ServerLevel, Map<Long, Set<TerrainWatcher>>> cleanWatchers = new HashMap<>();

    @SubscribeEvent
    public static void onLevelUnloaded(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)
            cleanWatchers.remove(level);
    }

    public static void markChanged(ServerLevel serverLevel, long pos) {
        BlockPos blockPos = BlockPos.of(pos);
        long sectionKey = SectionPos.asLong(blockPos);

        AABB aabb = new AABB(blockPos);
        cleanWatchers
                .getOrDefault(serverLevel, new HashMap<>())
                .getOrDefault(sectionKey, new HashSet<>())
                .removeIf(terrainWatcher -> {
                    if(terrainWatcher.cachedArea.intersects(aabb)){
                        terrainWatcher.setDirty();
                        return true;
                    } return false;
                });
    }

    public static void unregisterWatcher(ServerLevel serverLevel, TerrainWatcher watcher) {
        var hashMap = cleanWatchers.getOrDefault(serverLevel, new HashMap<>());
        for (Long section : getAllSections(watcher)) {
            hashMap.getOrDefault(section, new HashSet<>()).remove(watcher);
        }
    }

    public static void registerWatcher(ServerLevel serverLevel, TerrainWatcher watcher){
        var hashMap = cleanWatchers.computeIfAbsent(serverLevel, k -> new HashMap<>());
        for (Long section : getAllSections(watcher)) {
            hashMap.computeIfAbsent(section, k -> new HashSet<>()).add(watcher);
        }
    }

    private static List<Long> getAllSections(TerrainWatcher watcher){
        return SectionPos.betweenClosedStream(
                SectionPos.blockToSectionCoord(Math.floor(watcher.cachedArea.minX)),
                SectionPos.blockToSectionCoord(Math.floor(watcher.cachedArea.minY)),
                SectionPos.blockToSectionCoord(Math.floor(watcher.cachedArea.minZ)),
                SectionPos.blockToSectionCoord(Math.floor(Math.nextDown(watcher.cachedArea.maxX))),
                SectionPos.blockToSectionCoord(Math.floor(Math.nextDown(watcher.cachedArea.maxY))),
                SectionPos.blockToSectionCoord(Math.floor(Math.nextDown(watcher.cachedArea.maxZ)))
        ).map(SectionPos::asLong).toList();
    }
}
