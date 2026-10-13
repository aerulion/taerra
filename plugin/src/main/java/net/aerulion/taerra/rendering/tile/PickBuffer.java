package net.aerulion.taerra.rendering.tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PickBuffer {

  public static final int UNPAINTED = Integer.MIN_VALUE;

  public static final int FORMAT_VERSION = 3;

  public static final int HEADER_BYTES = 18;

  public static final int ISOMETRIC_SCALE = 2;

  public static final int ISOMETRIC_DEPTH_PER_HEIGHT = 2;

  public static final int MAX_SPAN = 0xFFFF;

  private static final int DEFLATE_LEVEL = 4;
  private static final byte @NotNull [] MAGIC = {'P', 'M', 'P', 'K'};
  private static final int LEGACY_VERSION = 2;
  private static final int LEGACY_HEADER_BYTES = 16;
  private static final int LEGACY_NO_VALUE = 0xFFFF;

  private final int size;
  private final int @NotNull [] depth;
  private final int @NotNull [] height;
  private final int scale;
  private final int depthPerHeight;

  public PickBuffer(final int size, final int @NotNull [] depth, final int @NotNull [] height) {
    this(size, depth, height, 1, 0);
  }

  public PickBuffer(
      final int size,
      final int @NotNull [] depth,
      final int @NotNull [] height,
      final int scale,
      final int depthPerHeight) {
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
    this.depthPerHeight = depthPerHeight;
  }

  public static @Nullable PickBuffer decode(final byte @NotNull [] raw) {
    if (raw.length < LEGACY_HEADER_BYTES
        || !Arrays.equals(raw, 0, MAGIC.length, MAGIC, 0, MAGIC.length)
        || (raw[4] != FORMAT_VERSION && raw[4] != LEGACY_VERSION)) {
      return null;
    }
    final boolean legacy = raw[4] == LEGACY_VERSION;
    final int headerBytes = legacy ? LEGACY_HEADER_BYTES : HEADER_BYTES;
    if (raw.length < headerBytes) {
      return null;
    }
    final int size = readShort(raw, 5);
    final int originDepth = readInt(raw, 7);
    final int originHeight = readInt(raw, 11);
    final int scale = raw[15] & 0xFF;
    final int depthPerHeight =
        legacy ? (scale == ISOMETRIC_SCALE ? ISOMETRIC_DEPTH_PER_HEIGHT : 0) : raw[16] & 0xFF;
    final int width = legacy ? 2 : raw[17] & 0xFF;
    if (scale == 0 || size % scale != 0 || (width != 1 && width != 2)) {
      return null;
    }
    final int stored = size / scale;
    final int cells = stored * stored;
    final int maskBytes = legacy ? 0 : (cells + 7) / 8;
    final byte @Nullable [] body = inflate(raw, headerBytes, maskBytes + cells * width * 2);
    if (body == null) {
      return null;
    }

    final int @NotNull [] cellDepth = new int[cells];
    final int @NotNull [] cellHeight = new int[cells];
    final int wrap = (1 << 8 * width) - 1;
    int previousDepth = 0;
    int previousHeight = 0;
    for (int cell = 0; cell < cells; cell++) {
      final int depthValue = readValue(body, maskBytes + cell * width, width);
      final int heightValue = readValue(body, maskBytes + (cells + cell) * width, width);
      if (legacy) {
        previousDepth = depthValue;
        previousHeight = heightValue;
      } else {
        previousDepth = ((cell >= stored ? cellDepth[cell - stored] : 0) + depthValue) & wrap;
        previousHeight = ((cell % stored > 0 ? previousHeight : 0) + heightValue) & wrap;
      }
      cellDepth[cell] = previousDepth;
      cellHeight[cell] = previousHeight;
    }

    final int @NotNull [] depth = new int[size * size];
    final int @NotNull [] height = new int[size * size];
    for (int index = 0; index < depth.length; index++) {
      final int cell = index / size / scale * stored + index % size / scale;
      final boolean empty =
          legacy
              ? cellDepth[cell] == LEGACY_NO_VALUE
              : (body[cell >> 3] & 0x80 >>> (cell & 7)) != 0;
      final int blockY = originHeight + cellHeight[cell];
      depth[index] =
          empty
              ? UNPAINTED
              : originDepth + cellDepth[cell] + (legacy ? 0 : depthPerHeight * blockY);
      height[index] = blockY;
    }
    return new PickBuffer(size, depth, height, scale, depthPerHeight);
  }

  private static byte @Nullable [] inflate(
      final byte @NotNull [] raw, final int offset, final int length) {
    final byte @NotNull [] out = new byte[length];
    try (final @NotNull Inflater inflater = new Inflater()) {
      inflater.setInput(raw, offset, raw.length - offset);
      int read = 0;
      while (read < length && !inflater.finished()) {
        final int chunk = inflater.inflate(out, read, length - read);
        if (chunk == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          break;
        }
        read += chunk;
      }
      return read == length ? out : null;
    } catch (final DataFormatException e) {
      return null;
    }
  }

  private static int readValue(final byte @NotNull [] source, final int offset, final int width) {
    return width == 1 ? source[offset] & 0xFF : readShort(source, offset);
  }

  private static int readShort(final byte @NotNull [] source, final int offset) {
    return (source[offset] & 0xFF) << 8 | source[offset + 1] & 0xFF;
  }

  private static int readInt(final byte @NotNull [] source, final int offset) {
    return (source[offset] & 0xFF) << 24
        | (source[offset + 1] & 0xFF) << 16
        | (source[offset + 2] & 0xFF) << 8
        | source[offset + 3] & 0xFF;
  }

  private static void writeValue(
      final byte @NotNull [] target, final int offset, final int width, final int value) {
    if (width == 1) {
      target[offset] = (byte) value;
    } else {
      writeShort(target, offset, value);
    }
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

  public int scale() {
    return this.scale;
  }

  public int @Nullable [] cell(final int x, final int y) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) {
      return null;
    }
    final int index = representative(x / this.scale, y / this.scale);
    return index < 0 ? null : new int[] {this.depth[index], this.height[index]};
  }

  public byte @Nullable [] encode() throws IOException {
    final int stored = this.size / this.scale;
    final int cells = stored * stored;
    final int @NotNull [] cellDepth = new int[cells];
    final int @NotNull [] cellHeight = new int[cells];
    final boolean @NotNull [] empty = new boolean[cells];
    long minDepth = Long.MAX_VALUE;
    long maxDepth = Long.MIN_VALUE;
    int minHeight = Integer.MAX_VALUE;
    int maxHeight = Integer.MIN_VALUE;
    for (int cell = 0; cell < cells; cell++) {
      final int index = representative(cell % stored, cell / stored);
      empty[cell] = index < 0;
      if (empty[cell]) {
        continue;
      }
      final long relative = this.depth[index] - (long) this.depthPerHeight * this.height[index];
      if (relative < Integer.MIN_VALUE || relative > Integer.MAX_VALUE) {
        return null;
      }
      cellDepth[cell] = (int) relative;
      cellHeight[cell] = this.height[index];
      minDepth = Math.min(minDepth, relative);
      maxDepth = Math.max(maxDepth, relative);
      minHeight = Math.min(minHeight, cellHeight[cell]);
      maxHeight = Math.max(maxHeight, cellHeight[cell]);
    }
    if (minDepth == Long.MAX_VALUE) {
      return null;
    }
    if (maxDepth - minDepth > MAX_SPAN || (long) maxHeight - minHeight > MAX_SPAN) {
      return null;
    }

    final int width = maxDepth - minDepth <= 0xFF && maxHeight - minHeight <= 0xFF ? 1 : 2;
    final int maskBytes = (cells + 7) / 8;
    final byte @NotNull [] body = new byte[maskBytes + cells * width * 2];
    int previousHeight = 0;
    for (int cell = 0; cell < cells; cell++) {
      final int predictedDepth = cell >= stored ? cellDepth[cell - stored] : 0;
      final int predictedHeight = cell % stored > 0 ? previousHeight : 0;
      if (empty[cell]) {
        body[cell >> 3] |= (byte) (0x80 >>> (cell & 7));
        cellDepth[cell] = predictedDepth;
        cellHeight[cell] = predictedHeight;
      } else {
        cellDepth[cell] -= (int) minDepth;
        cellHeight[cell] -= minHeight;
      }
      previousHeight = cellHeight[cell];
      writeValue(body, maskBytes + cell * width, width, cellDepth[cell] - predictedDepth);
      writeValue(
          body, maskBytes + (cells + cell) * width, width, cellHeight[cell] - predictedHeight);
    }

    final @NotNull ByteArrayOutputStream out =
        new ByteArrayOutputStream(HEADER_BYTES + body.length / 4);
    final byte @NotNull [] header = new byte[HEADER_BYTES];
    System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
    header[4] = FORMAT_VERSION;
    writeShort(header, 5, this.size);
    writeInt(header, 7, (int) minDepth);
    writeInt(header, 11, minHeight);
    header[15] = (byte) this.scale;
    header[16] = (byte) this.depthPerHeight;
    header[17] = (byte) width;
    out.write(header);

    try (final @NotNull Deflater deflater = new Deflater(DEFLATE_LEVEL);
        final @NotNull DeflaterOutputStream compressed = new DeflaterOutputStream(out, deflater)) {
      compressed.write(body);
    }
    return out.toByteArray();
  }
}
