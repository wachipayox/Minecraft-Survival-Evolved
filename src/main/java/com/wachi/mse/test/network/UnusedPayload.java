package com.wachi.mse.test.network;

import com.wachi.mse.MseMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = MseMod.MOD_ID)
public record UnusedPayload(int index) implements CustomPacketPayload {

    public static final Type<UnusedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MseMod.MOD_ID, "unused")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UnusedPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeInt(payload.index()),
                    (buffer) -> new UnusedPayload(buffer.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UnusedPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

    }

    @SubscribeEvent
    public static void registerMessage(RegisterPayloadHandlersEvent event) {
        event.registrar(MseMod.NETWORK_VERSION)
                .playToClient(TYPE, STREAM_CODEC, UnusedPayload::handle);
    }
}
