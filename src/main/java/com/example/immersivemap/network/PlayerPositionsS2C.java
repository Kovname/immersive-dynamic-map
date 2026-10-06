package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Positions of every visible player in the receiver's dimension, including ones outside tracking range. */
public record PlayerPositionsS2C(List<Entry> players) implements CustomPayload {
    public static final Id<PlayerPositionsS2C> ID = new Id<>(ImmersiveMapMod.id("player_positions"));
    public static final PacketCodec<RegistryByteBuf, PlayerPositionsS2C> CODEC =
            CustomPayload.codecOf(PlayerPositionsS2C::write, PlayerPositionsS2C::read);
    private static final int MAX_PLAYERS = 1024;

    public record Entry(UUID id, double x, double z, float yaw) {
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(players.size());
        for (Entry entry : players) {
            buf.writeUuid(entry.id());
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.z());
            buf.writeFloat(entry.yaw());
        }
    }

    private static PlayerPositionsS2C read(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many players: " + count);
        }
        List<Entry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new Entry(buf.readUuid(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
        }
        return new PlayerPositionsS2C(players);
    }

    @Override
    public Id<PlayerPositionsS2C> getId() {
        return ID;
    }
}
