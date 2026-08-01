package com.wachi.mse.network;

import com.wachi.mse.entity.dinosaur.DinosaurEntity;
import com.wachi.mse.entity.dinosaur.hitbox.DinosaurPartEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MseNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private MseNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(
                DinosaurSupportVetoPayload.TYPE,
                DinosaurSupportVetoPayload.STREAM_CODEC,
                MseNetworking::handleSupportVeto);
    }

    private static void handleSupportVeto(
            DinosaurSupportVetoPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity candidate = player.level().getEntity(
                payload.dinosaurEntityId());
        if (!(candidate instanceof DinosaurEntity dinosaur)) {
            return;
        }
        PartEntity<?>[] parts = dinosaur.getParts();
        if (payload.partIndex() < 0
                || payload.partIndex() >= parts.length) {
            return;
        }
        if (parts[payload.partIndex()]
                instanceof DinosaurPartEntity part) {
            part.setServerSupportVeto(
                    player,
                    payload.vetoed());
        }
    }
}
