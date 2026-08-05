package com.wachi.mse.test.collide.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.*;

@EventBusSubscriber
public class TerrainChangeTracker {

    private static final Map<Level, Map<Long, Set<TerrainWatcher>>> cleanWatchers = new HashMap<>();

    @SubscribeEvent
    public static void onLevelUnloaded(LevelEvent.Unload event) {
        if(event.getLevel() instanceof Level level)
            cleanWatchers.remove(level);
    }

    public static void markChanged(Level level, long pos) {
        BlockPos blockPos = BlockPos.of(pos);
        long sectionKey = SectionPos.asLong(blockPos);

        AABB aabb = new AABB(blockPos);
        cleanWatchers
                .getOrDefault(level, new HashMap<>())
                .getOrDefault(sectionKey, new HashSet<>())
                .removeIf(terrainWatcher -> {
                    if(terrainWatcher.cachedArea.intersects(aabb)){
                        terrainWatcher.setDirty();
                        return true;
                    } return false;
                });
    }

    public static void unregisterWatcher(Level level, TerrainWatcher watcher) {
        var hashMap = cleanWatchers.getOrDefault(level, new HashMap<>());
        for (Long section : getAllSections(watcher)) {
            hashMap.getOrDefault(section, new HashSet<>()).remove(watcher);
        }
    }

    public static void registerWatcher(Level level, TerrainWatcher watcher){
        var hashMap = cleanWatchers.computeIfAbsent(level, k -> new HashMap<>());
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
