package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Sent by a modded client right after joining; the server answers with {@link ServerHelloS2C}. */
public record ClientHelloC2S(int protocol) implements CustomPayload {
    public static final Id<ClientHelloC2S> ID = new Id<>(ImmersiveMapMod.id("client_hello"));
    public static final PacketCodec<RegistryByteBuf, ClientHelloC2S> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, ClientHelloC2S::protocol, ClientHelloC2S::new);

    @Override
    public Id<ClientHelloC2S> getId() {
        return ID;
    }
}
