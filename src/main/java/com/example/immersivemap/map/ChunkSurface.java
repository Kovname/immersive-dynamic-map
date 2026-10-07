package com.example.immersivemap.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * The inputs of the vanilla map algorithm for each block column of a chunk: map color id, surface height and
 * water depth. Coarser zoom levels are aggregated from these exactly like {@code FilledMapItem#updateColors}
 * does, so every zoom level keeps vanilla colors and shading.
 *
 * <p>A color byte is the {@link net.minecraft.block.MapColor} id, optionally with {@link #WALL_FLAG} set for cave
 * walls, or {@link #VOID_COLOR} for an explored column without any blocks (the End void).
 */
public final class ChunkSurface {
    public static final int AREA = 256;
    public static final int RAW_BYTES = AREA * 4;
    public static final int MAX_SCALE = 4;
    /** Set on cave walls: solid rock next to explored space, drawn in the darkest vanilla shade. */
    public static final int WALL_FLAG = 0x40;
    public static final int COLOR_MASK = 0x3F;
    /** Unused by vanilla ({@code MapColor.get(63)} is clear), so older clients simply skip it. */
    public static final int VOID_COLOR = 63;

    /** Color byte per column (see class docs), 0 = not explored. */
    public final byte[] colors = new byte[AREA];
    public final short[] heights = new short[AREA];
    /** Unsigned water depth below the surface block. */
    public final byte[] depths = new byte[AREA];
    public long timestamp;

    private final byte[][] aggregateColors = new byte[MAX_SCALE + 1][];
    private final float[][] aggregateHeights = new float[MAX_SCALE + 1][];
    private final byte[][] aggregateDepths = new byte[MAX_SCALE + 1][];

    public static int index(int localX, int localZ) {
        return localZ << 4 | localX;
    }

    public static boolean isWall(int color) {
        return (color & WALL_FLAG) != 0;
    }

    public boolean isExplored(int index) {
        return colors[index] != 0;
    }

    public boolean isEmpty() {
        for (byte color : colors) {
            if (color != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean set(int index, int color, int height, int depth) {
        byte c = (byte) color;
        short h = (short) height;
        byte d = (byte) Math.min(Math.max(depth, 0), 255);
        if (colors[index] == c && heights[index] == h && depths[index] == d) {
            return false;
        }
        colors[index] = c;
        heights[index] = h;
        depths[index] = d;
        invalidate();
        return true;
    }

    /** Copies every explored column of {@code other} into this chunk. */
    public boolean mergeFrom(ChunkSurface other) {
        boolean changed = false;
        for (int i = 0; i < AREA; i++) {
            if (other.colors[i] != 0
                    && (colors[i] != other.colors[i] || heights[i] != other.heights[i] || depths[i] != other.depths[i])) {
                colors[i] = other.colors[i];
                heights[i] = other.heights[i];
                depths[i] = other.depths[i];
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
        return changed;
    }

    public ChunkSurface copy() {
        ChunkSurface copy = new ChunkSurface();
        System.arraycopy(colors, 0, copy.colors, 0, AREA);
        System.arraycopy(heights, 0, copy.heights, 0, AREA);
        System.arraycopy(depths, 0, copy.depths, 0, AREA);
        copy.timestamp = timestamp;
        return copy;
    }

    public void invalidate() {
        for (int scale = 1; scale <= MAX_SCALE; scale++) {
            aggregateColors[scale] = null;
        }
    }

    /** Color ids of the cells at {@code scale}; a chunk holds {@code (16 >> scale)^2} cells. */
    public byte[] colors(int scale) {
        if (scale == 0) {
            return colors;
        }
        ensureAggregated(scale);
        return aggregateColors[scale];
    }

    /** Only valid after {@link #colors(int)} was called for the same scale. */
    public float height(int scale, int cell) {
        return scale == 0 ? heights[cell] : aggregateHeights[scale][cell];
    }

    /** Only valid after {@link #colors(int)} was called for the same scale. */
    public int depth(int scale, int cell) {
        return (scale == 0 ? depths[cell] : aggregateDepths[scale][cell]) & 0xFF;
    }

    private void ensureAggregated(int scale) {
        if (aggregateColors[scale] != null) {
            return;
        }

        int block = 1 << scale;
        int cells = 16 >> scale;
        byte[] outColors = new byte[cells * cells];
        float[] outHeights = new float[cells * cells];
        byte[] outDepths = new byte[cells * cells];
        int[] counts = new int[128];
        int[] order = new int[block * block];
        // Floors, walls and void are averaged separately so walls never skew floor shading.
        long[] heightSums = new long[3];
        int[] depthSums = new int[3];
        int[] classCounts = new int[3];

        for (int cellZ = 0; cellZ < cells; cellZ++) {
            for (int cellX = 0; cellX < cells; cellX++) {
                int distinct = 0;
                int explored = 0;
                heightSums[0] = heightSums[1] = heightSums[2] = 0L;
                depthSums[0] = depthSums[1] = depthSums[2] = 0;
                classCounts[0] = classCounts[1] = classCounts[2] = 0;
                // Same iteration order as vanilla so ties between colors resolve identically.
                for (int u = 0; u < block; u++) {
                    for (int v = 0; v < block; v++) {
                        int i = index(cellX * block + u, cellZ * block + v);
                        int color = colors[i] & 0x7F;
                        if (color == 0) {
                            continue;
                        }
                        if (counts[color]++ == 0) {
                            order[distinct++] = color;
                        }
                        explored++;
                        int type = classOf(color);
                        classCounts[type]++;
                        heightSums[type] += heights[i];
                        depthSums[type] += depths[i] & 0xFF;
                    }
                }

                int cell = cellZ * cells + cellX;
                if (explored == 0) {
                    continue;
                }

                int best = order[0];
                for (int k = 1; k < distinct; k++) {
                    if (counts[order[k]] > counts[best]) {
                        best = order[k];
                    }
                }
                for (int k = 0; k < distinct; k++) {
                    counts[order[k]] = 0;
                }

                int type = classOf(best);
                outColors[cell] = (byte) best;
                outHeights[cell] = (float) heightSums[type] / classCounts[type];
                outDepths[cell] = (byte) (depthSums[type] / classCounts[type]);
            }
        }

        aggregateHeights[scale] = outHeights;
        aggregateDepths[scale] = outDepths;
        aggregateColors[scale] = outColors;
    }

    private static int classOf(int color) {
        return color == VOID_COLOR ? 2 : isWall(color) ? 1 : 0;
    }

    public void writeRaw(DataOutput out) throws IOException {
        out.write(colors);
        for (short height : heights) {
            out.writeShort(height);
        }
        out.write(depths);
    }

    public void readRaw(DataInput in) throws IOException {
        in.readFully(colors);
        for (int i = 0; i < AREA; i++) {
            heights[i] = in.readShort();
            if ((colors[i] & 0xFF) >= 128) {
                colors[i] = 0;
            }
        }
        in.readFully(depths);
        invalidate();
    }

    public byte[] compress() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater))) {
            writeRaw(out);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    /** Rejects anything that does not inflate to exactly one chunk, so untrusted input cannot balloon. */
    public static ChunkSurface decompress(byte[] data, long timestamp) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] raw = new byte[RAW_BYTES];
            int read = 0;
            while (read < RAW_BYTES && !inflater.finished()) {
                int n = inflater.inflate(raw, read, RAW_BYTES - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                read += n;
            }
            if (read != RAW_BYTES || !inflater.finished()) {
                throw new IOException("Malformed map chunk");
            }
            ChunkSurface surface = new ChunkSurface();
            surface.readRaw(new DataInputStream(new ByteArrayInputStream(raw)));
            surface.timestamp = timestamp;
            return surface;
        } catch (DataFormatException exception) {
            throw new IOException(exception);
        } finally {
            inflater.end();
        }
    }
}
