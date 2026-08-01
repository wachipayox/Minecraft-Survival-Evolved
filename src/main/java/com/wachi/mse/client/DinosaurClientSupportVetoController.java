package com.wachi.mse.client;

import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPartEntity;
import com.wachi.mse.network.DinosaurSupportVetoPayload;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Gives the local player a negative-only veto over phantom support produced
 * by the axis-aligned envelope of a rotated dinosaur bone box.
 */
public final class DinosaurClientSupportVetoController {
    private static final int KEEPALIVE_INTERVAL_TICKS = 2;
    private static final double SEARCH_HORIZONTAL_MARGIN = 0.25;
    private static final double COLLISION_PROBE_MARGIN = 0.08;
    private static final double SURFACE_PROBE_THICKNESS = 0.06;
    private static final double JUMP_PROBE_HEIGHT = 0.60;
    private static final double MAX_PROBE_MOVEMENT = 2.0;

    private static final Set<DinosaurPartEntity> ACTIVE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<DinosaurPartEntity, Long> LAST_SENT =
            new IdentityHashMap<>();

    private DinosaurClientSupportVetoController() {
    }

    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clearLocalState();
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        Set<DinosaurPartEntity> next =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Vec3 movement = player.getDeltaMovement();
        if (minecraft.options.keyJump.isDown()) {
            movement = new Vec3(
                    movement.x,
                    Math.max(movement.y, JUMP_PROBE_HEIGHT),
                    movement.z);
        }
        if (movement.lengthSqr()
                > MAX_PROBE_MOVEMENT * MAX_PROBE_MOVEMENT) {
            movement = movement.normalize().scale(
                    MAX_PROBE_MOVEMENT);
        }
        AABB collisionProbe = player.getBoundingBox()
                .expandTowards(movement)
                .inflate(COLLISION_PROBE_MARGIN);
        AABB searchArea = collisionProbe.inflate(
                SEARCH_HORIZONTAL_MARGIN);
        for (Entity entity : minecraft.level.getEntities(
                player,
                searchArea,
                candidate -> candidate
                        instanceof DinosaurPartEntity)) {
            DinosaurPartEntity part = (DinosaurPartEntity) entity;
            if (!part.isBroadCollisionCandidate(
                    player,
                    collisionProbe)) {
                continue;
            }
            AABB precisionProbe = precisionProbe(
                    player,
                    part,
                    movement,
                    collisionProbe);
            if (!part.preciselyIntersects(precisionProbe)) {
                next.add(part);
            } else if (!ACTIVE.contains(part)) {
                part.pushAboveIfEmbedded(player);
            }
        }

        for (DinosaurPartEntity part : ACTIVE) {
            if (!next.contains(part)) {
                part.setClientSupportVeto(player, false);
                send(part, false);
                LAST_SENT.remove(part);
            }
        }
        for (DinosaurPartEntity part : next) {
            part.setClientSupportVeto(player, true);
            long lastSent = LAST_SENT.getOrDefault(
                    part,
                    Long.MIN_VALUE);
            if (!ACTIVE.contains(part)
                    || gameTime - lastSent
                            >= KEEPALIVE_INTERVAL_TICKS) {
                send(part, true);
                LAST_SENT.put(part, gameTime);
            }
        }

        ACTIVE.clear();
        ACTIVE.addAll(next);
    }

    private static AABB precisionProbe(
            LocalPlayer player,
            DinosaurPartEntity part,
            Vec3 movement,
            AABB fullBodyProbe) {
        AABB body = player.getBoundingBox();
        if (movement.y < -1.0E-4
                || part.preciselySupports(player)) {
            AABB feet = new AABB(
                    body.minX,
                    body.minY - SURFACE_PROBE_THICKNESS,
                    body.minZ,
                    body.maxX,
                    body.minY + SURFACE_PROBE_THICKNESS,
                    body.maxZ);
            return feet.minmax(feet.move(movement));
        }
        if (movement.y > 1.0E-4) {
            AABB head = new AABB(
                    body.minX,
                    body.maxY - SURFACE_PROBE_THICKNESS,
                    body.minZ,
                    body.maxX,
                    body.maxY + SURFACE_PROBE_THICKNESS,
                    body.maxZ);
            return head.minmax(head.move(movement));
        }
        return fullBodyProbe;
    }

    private static void send(
            DinosaurPartEntity part,
            boolean vetoed) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new DinosaurSupportVetoPayload(
                        part.getParent().getId(),
                        part.partIndex(),
                        vetoed));
    }

    private static void clearLocalState() {
        ACTIVE.clear();
        LAST_SENT.clear();
    }
}
