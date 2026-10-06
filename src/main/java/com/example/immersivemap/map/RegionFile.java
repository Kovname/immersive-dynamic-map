package com.example.immersivemap.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** 32x32 chunks of one layer, each stored as an individually deflated {@link ChunkSurface}. */
public final class RegionFile {
    public static final int SHIFT = 5;
    public static final int SIZE = 1 << SHIFT;
    public static final int CHUNKS = SIZE * SIZE;
    private static final int MAGIC = 0x494D5232;
    private static final int VERSION = 1;
    private static final int MAX_ENTRY_BYTES = 8192;

    private RegionFile() {
    }

    public record Entry(int index, long timestamp, byte[] data) {
    }

    public static int localIndex(int chunkX, int chunkZ) {
        return (chunkZ & (SIZE - 1)) << SHIFT | (chunkX & (SIZE - 1));
    }

    public static String fileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".dat";
    }

    public static List<Entry> read(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("Unknown map region format: " + file);
            }
            int count = in.readInt();
            if (count < 0 || count > CHUNKS) {
                throw new IOException("Corrupt map region: " + file);
            }
            for (int i = 0; i < count; i++) {
                int index = in.readUnsignedShort();
                long timestamp = in.readLong();
                int length = in.readInt();
                if (index >= CHUNKS || length < 0 || length > MAX_ENTRY_BYTES) {
                    throw new IOException("Corrupt map region: " + file);
                }
                byte[] data = new byte[length];
                in.readFully(data);
                entries.add(new Entry(index, timestamp, data));
            }
        }
        return entries;
    }

    public static void write(Path file, List<Entry> entries) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeShort(entry.index());
                out.writeLong(entry.timestamp());
                out.writeInt(entry.data().length);
                out.write(entry.data());
            }
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
