package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.HoldState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Tells tracking clients how another player holds the map, so they can pose that player. */
public record PlayerHoldStateS2C(int entityId, HoldState state) implements CustomPayload {
    public static final Id<PlayerHoldStateS2C> ID = new Id<>(ImmersiveMapMod.id("player_hold_state"));
    public static final PacketCodec<RegistryByteBuf, PlayerHoldStateS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PlayerHoldStateS2C::entityId,
            PacketCodecs.VAR_INT.xmap(HoldState::byId, HoldState::ordinal), PlayerHoldStateS2C::state,
            PlayerHoldStateS2C::new);

    @Override
    public Id<PlayerHoldStateS2C> getId() {
        return ID;
    }
}
