package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import com.example.immersivemap.map.LayerId;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared map chunks, used in both directions. {@code watermark} is only meaningful server to client: when it is
 * not negative the client has received everything the server stored up to that time.
 */
public record MapChunksPayload(List<Entry> entries, long watermark) implements CustomPayload {
    public static final Id<MapChunksPayload> ID = new Id<>(ImmersiveMapMod.id("map_chunks"));
    public static final PacketCodec<RegistryByteBuf, MapChunksPayload> CODEC =
            CustomPayload.codecOf(MapChunksPayload::write, MapChunksPayload::read);
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_DATA_BYTES = 8192;

    public record Entry(LayerId layer, int chunkX, int chunkZ, long timestamp, byte[] data) {
        public int estimatedSize() {
            return data.length + 48;
        }
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarLong(watermark + 1L);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.layer().write(buf);
            buf.writeInt(entry.chunkX());
            buf.writeInt(entry.chunkZ());
            buf.writeVarLong(entry.timestamp());
            buf.writeByteArray(entry.data());
        }
    }

    private static MapChunksPayload read(RegistryByteBuf buf) {
        long watermark = buf.readVarLong() - 1L;
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many map chunks: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LayerId layer = LayerId.read(buf);
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            long timestamp = buf.readVarLong();
            byte[] data = buf.readByteArray(MAX_DATA_BYTES);
            entries.add(new Entry(layer, chunkX, chunkZ, timestamp, data));
        }
        return new MapChunksPayload(entries, watermark);
    }

    @Override
    public Id<MapChunksPayload> getId() {
        return ID;
    }
}
