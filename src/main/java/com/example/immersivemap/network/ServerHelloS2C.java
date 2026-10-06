package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Features the server enabled for this connection. */
public record ServerHelloS2C(int protocol, boolean playerPositions, boolean heldMapVisible, boolean mapSync)
        implements CustomPayload {
    public static final Id<ServerHelloS2C> ID = new Id<>(ImmersiveMapMod.id("server_hello"));
    public static final PacketCodec<RegistryByteBuf, ServerHelloS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ServerHelloS2C::protocol,
            PacketCodecs.BOOL, ServerHelloS2C::playerPositions,
            PacketCodecs.BOOL, ServerHelloS2C::heldMapVisible,
            PacketCodecs.BOOL, ServerHelloS2C::mapSync,
            ServerHelloS2C::new);

    @Override
    public Id<ServerHelloS2C> getId() {
        return ID;
    }
}
