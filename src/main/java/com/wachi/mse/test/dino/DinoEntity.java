package com.wachi.mse.test.dino;

import com.wachi.mse.test.collide.terrain.TerrainWatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DinoEntity extends PathfinderMob {

    public final List<DinoLegPair<DinoEntity>> hips = new ArrayList<>();

    private Map<DinoLeg<DinoEntity>, TerrainWatcher> terrainWatchers = new HashMap<>();

    public DinoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setupParts();
    }

    public void setupParts(){
        double m = 0;
        hips.add(
                new DinoLegPair<>(
                        this,
                        new DinoLegBendConfig(),
                        new Vec3(m, 0.5, m),
                        0.3,
                        0.25,
                        0.25,
                        0.1
                )
        );
    }

    protected void setupTerrainWatcherMap(){
        terrainWatchers.clear();
        double mergeMargin = 1;

        for (DinoLegPair<DinoEntity> hip : hips) {
            var free = new ArrayList<>(hip.getLegs());

            for (DinoLeg<DinoEntity> leg : hip.getLegs()) {
                if(!free.contains(leg)) continue;

                var legBounds = leg.getBounds();

                List<DinoLeg<DinoEntity>> removing = new ArrayList<>(List.of(leg));
                for (int i = 0; i < free.size(); i++) {
                    var freeLeg = free.get(i);
                    if(removing.contains(freeLeg)) continue;

                    var bounds = freeLeg.getBounds();

                    if(
                            removing.stream().anyMatch(
                                    removingLeg -> bounds
                                            .inflate(mergeMargin)
                                            .intersects(removingLeg.getBounds())
                            )
                    ) {
                        i = -1;
                        legBounds = legBounds.minmax(bounds);
                        removing.add(freeLeg);
                    }
                }
                free.removeAll(removing);

                var tWatcher = new TerrainWatcher(
                        hip.getCenter(1),
                        legBounds.expandTowards(0, -DinoLeg.getGroundQueryEpsilon(), 0),
                        this,
                        1
                );
                for (DinoLeg<DinoEntity> removingLeg : removing) {
                    terrainWatchers.put(removingLeg, tWatcher);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if(terrainWatchers.isEmpty()) setupTerrainWatcherMap();

        List<TerrainWatcher> ticked = new ArrayList<>();
        terrainWatchers.forEach((leg, watcher) -> {
            if(ticked.contains(watcher)) return;
            ticked.add(watcher);
            watcher.tick(level(), leg.parent.getCenter(1));
        });

        for (DinoLegPair<DinoEntity> hip : hips) {
            hip.tick(1);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        setYBodyRot(0);
        hips.getFirst().setYRot((float) Math.toRadians(0));
        hips.getFirst().getLegs().forEach(
                leg -> leg.state = DinoLeg.DLegState.CHILL
        );

        hips.getFirst().getLeftLeg().dinoFoot.actualPose
                = new DinoFoot.DFootPose((float) Math.toRadians(-25), (float) Math.toRadians(15));


//        hips.getFirst().getLeftLeg().upperXRot = (float) Math.toRadians(0);
//        hips.getFirst().getLeftLeg().kneeXRot = (float) Math.toRadians(0);
        return super.interact(player, hand, location);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    public List<DinoLeg<DinoEntity>> getLegs() {
        var list = new ArrayList<DinoLeg<DinoEntity>>();
        for (DinoLegPair<DinoEntity> hip : hips) {
            list.addAll(hip.getLegs());
        }
        return list;
    }

    public Map<DinoLeg<DinoEntity>, TerrainWatcher> getTerrainWatchers() {
        return new HashMap<>(terrainWatchers);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity other) {
    }

    @Override
    public boolean isColliding(BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void setId(int id) {
        super.setId(id);
    }

    @Override
    public void aiStep() {
        super.aiStep();
    }
}
