package com.wachi.mse.network;

import com.wachi.mse.MseMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A client may only reject its own support collision. It cannot create
 * support or alter combat, selection, position, velocity or on-ground state.
 */
public record DinosaurSupportVetoPayload(
        int dinosaurEntityId,
        int partIndex,
        boolean vetoed) implements CustomPacketPayload {
    public static final Type<DinosaurSupportVetoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    MseMod.MOD_ID,
                    "dinosaur_support_veto"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DinosaurSupportVetoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    DinosaurSupportVetoPayload::dinosaurEntityId,
                    ByteBufCodecs.VAR_INT,
                    DinosaurSupportVetoPayload::partIndex,
                    ByteBufCodecs.BOOL,
                    DinosaurSupportVetoPayload::vetoed,
                    DinosaurSupportVetoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
