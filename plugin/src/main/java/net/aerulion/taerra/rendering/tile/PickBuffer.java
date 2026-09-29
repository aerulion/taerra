package net.aerulion.taerra.rendering.tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PickBuffer {

  public static final int UNPAINTED = Integer.MIN_VALUE;

  public static final int NO_VALUE = 0xFFFF;

  public static final int FORMAT_VERSION = 2;

  public static final int HEADER_BYTES = 16;

  public static final int ISOMETRIC_SCALE = 2;

  private static final byte @NotNull [] MAGIC = {'P', 'M', 'P', 'K'};
  private static final int MAX_OFFSET = NO_VALUE - 1;

  private final int size;
  private final int @NotNull [] depth;
  private final int @NotNull [] height;
  private final int scale;

  public PickBuffer(final int size, final int @NotNull [] depth, final int @NotNull [] height) {
    this(size, depth, height, 1);
  }

  public PickBuffer(
      final int size, final int @NotNull [] depth, final int @NotNull [] height, final int scale) {
    super();
    if (depth.length != size * size || height.length != size * size) {
      throw new IllegalArgumentException("Pick planes must hold " + size * size + " values");
    }
    if (scale < 1 || size % scale != 0) {
      throw new IllegalArgumentException("Scale " + scale + " does not divide size " + size);
    }
    this.size = size;
    this.depth = depth;
    this.height = height;
    this.scale = scale;
  }

  private static void writeShort(final byte @NotNull [] target, final int offset, final int value) {
    target[offset] = (byte) (value >>> 8);
    target[offset + 1] = (byte) value;
  }

  private static void writeInt(final byte @NotNull [] target, final int offset, final int value) {
    target[offset] = (byte) (value >>> 24);
    target[offset + 1] = (byte) (value >>> 16);
    target[offset + 2] = (byte) (value >>> 8);
    target[offset + 3] = (byte) value;
  }

  private int representative(final int cellX, final int cellY) {
    for (int offsetY = 0; offsetY < this.scale; offsetY++) {
      for (int offsetX = 0; offsetX < this.scale; offsetX++) {
        final int index = (cellY * this.scale + offsetY) * this.size + cellX * this.scale + offsetX;
        if (this.depth[index] != UNPAINTED) {
          return index;
        }
      }
    }
    return -1;
  }

  public int size() {
    return this.size;
  }

  public byte @Nullable [] encode() throws IOException {
    int minDepth = Integer.MAX_VALUE;
    int maxDepth = Integer.MIN_VALUE;
    int minHeight = Integer.MAX_VALUE;
    int maxHeight = Integer.MIN_VALUE;
    for (int index = 0; index < this.depth.length; index++) {
      if (this.depth[index] == UNPAINTED) {
        continue;
      }
      minDepth = Math.min(minDepth, this.depth[index]);
      maxDepth = Math.max(maxDepth, this.depth[index]);
      minHeight = Math.min(minHeight, this.height[index]);
      maxHeight = Math.max(maxHeight, this.height[index]);
    }
    if (minDepth == Integer.MAX_VALUE) {
      return null;
    }
    if (maxDepth - minDepth > MAX_OFFSET || maxHeight - minHeight > MAX_OFFSET) {
      return null;
    }

    final int stored = this.size / this.scale;
    final int pixels = stored * stored;
    final byte @NotNull [] planes = new byte[pixels * 4];
    for (int cell = 0; cell < pixels; cell++) {
      final int index = representative(cell % stored, cell / stored);
      if (index < 0) {
        writeShort(planes, cell * 2, NO_VALUE);
        continue;
      }
      writeShort(planes, cell * 2, this.depth[index] - minDepth);
      writeShort(planes, (pixels + cell) * 2, this.height[index] - minHeight);
    }

    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_BYTES + pixels);
    final byte @NotNull [] header = new byte[HEADER_BYTES];
    System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
    header[4] = FORMAT_VERSION;
    writeShort(header, 5, this.size);
    writeInt(header, 7, minDepth);
    writeInt(header, 11, minHeight);
    header[15] = (byte) this.scale;
    out.write(header);

    try (final @NotNull Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        final @NotNull DeflaterOutputStream compressed = new DeflaterOutputStream(out, deflater)) {
      compressed.write(planes);
    }
    return out.toByteArray();
  }
}
