package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.HoldState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record HoldStateC2S(HoldState state) implements CustomPayload {
    public static final Id<HoldStateC2S> ID = new Id<>(ImmersiveMapMod.id("hold_state"));
    public static final PacketCodec<RegistryByteBuf, HoldStateC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.xmap(HoldState::byId, HoldState::ordinal), HoldStateC2S::state,
            HoldStateC2S::new);

    @Override
    public Id<HoldStateC2S> getId() {
        return ID;
    }
}
