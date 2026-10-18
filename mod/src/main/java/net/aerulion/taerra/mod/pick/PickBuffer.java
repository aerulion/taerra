package net.aerulion.taerra.mod.pick;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PickBuffer(int size, int scale, int[] depths, int[] heights) {

  private static final int HEADER_BYTES = 18;
  private static final int FORMAT_VERSION = 3;
  private static final int EMPTY = Integer.MIN_VALUE;

  public static @Nullable PickBuffer decode(final byte @NotNull [] raw) {
    if (raw.length < HEADER_BYTES
        || raw[0] != 'P'
        || raw[1] != 'M'
        || raw[2] != 'P'
        || raw[3] != 'K'
        || raw[4] != FORMAT_VERSION) {
      return null;
    }
    final int size = readShort(raw, 5);
    final int scale = raw[15] & 0xFF;
    final int depthPerHeight = raw[16] & 0xFF;
    final int width = raw[17] & 0xFF;
    if (scale == 0 || size % scale != 0 || (width != 1 && width != 2)) {
      return null;
    }
    final int stored = size / scale;
    final int cells = stored * stored;
    final int maskBytes = (cells + 7) / 8;
    final byte @NotNull [] body;
    try (final @NotNull Inflater inflater = new Inflater()) {
      inflater.setInput(raw, HEADER_BYTES, raw.length - HEADER_BYTES);
      final @NotNull ByteArrayOutputStream out =
          new ByteArrayOutputStream(maskBytes + cells * width * 2);
      final byte @NotNull [] chunk = new byte[8192];
      while (!inflater.finished()) {
        final int read = inflater.inflate(chunk);
        if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          break;
        }
        out.write(chunk, 0, read);
      }
      body = out.toByteArray();
    } catch (final DataFormatException e) {
      return null;
    }
    if (body.length < maskBytes + cells * width * 2) {
      return null;
    }
    final int originDepth = readInt(raw, 7);
    final int originHeight = readInt(raw, 11);
    final int wrap = (1 << 8 * width) - 1;
    final int @NotNull [] depths = new int[cells];
    final int @NotNull [] heights = new int[cells];
    int height = 0;
    for (int cell = 0; cell < cells; cell++) {
      final int depth =
          ((cell >= stored ? depths[cell - stored] : 0)
                  + readValue(body, maskBytes + cell * width, width))
              & wrap;
      height =
          ((cell % stored > 0 ? height : 0)
                  + readValue(body, maskBytes + (cells + cell) * width, width))
              & wrap;
      depths[cell] = depth;
      heights[cell] = height;
    }
    for (int cell = 0; cell < cells; cell++) {
      heights[cell] += originHeight;
      depths[cell] =
          (body[cell >> 3] & 0x80 >>> (cell & 7)) != 0
              ? EMPTY
              : originDepth + depths[cell] + depthPerHeight * heights[cell];
    }
    return new PickBuffer(size, scale, depths, heights);
  }

  private static int readValue(final byte @NotNull [] raw, final int offset, final int width) {
    return width == 1 ? raw[offset] & 0xFF : readShort(raw, offset);
  }

  private static int readShort(final byte @NotNull [] raw, final int offset) {
    return ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
  }

  private static int readInt(final byte @NotNull [] raw, final int offset) {
    return ((raw[offset] & 0xFF) << 24)
        | ((raw[offset + 1] & 0xFF) << 16)
        | ((raw[offset + 2] & 0xFF) << 8)
        | (raw[offset + 3] & 0xFF);
  }

  public static int[] isometricBlock(
      final int rotation,
      final int blockWidth,
      final int screenX,
      final int depth,
      final int height) {
    final int half = blockWidth / 2;
    final int higher = Math.floorDiv(screenX + half, half);
    final int difference = ((higher ^ depth) & 1) == 0 ? higher : higher - 1;
    final int rotatedX = (depth + difference) / 2;
    final int rotatedZ = (depth - difference) / 2;
    return switch (rotation) {
      case 1 -> new int[] {-rotatedZ - 1, height, rotatedX};
      case 2 -> new int[] {-rotatedX - 1, height, -rotatedZ - 1};
      case 3 -> new int[] {rotatedZ, height, -rotatedX - 1};
      default -> new int[] {rotatedX, height, rotatedZ};
    };
  }

  public int @Nullable [] cell(final int x, final int y) {
    final int stored = this.size / this.scale;
    final int index = Math.floorDiv(y, this.scale) * stored + Math.floorDiv(x, this.scale);
    if (x < 0 || y < 0 || x >= this.size || y >= this.size || this.depths[index] == EMPTY) {
      return null;
    }
    return new int[] {this.depths[index], this.heights[index]};
  }

  public @NotNull PickBuffer under(final @Nullable PickBuffer body) {
    if (body == null || body.size != this.size || body.scale != this.scale) {
      return this;
    }
    final int @NotNull [] depths = this.depths.clone();
    final int @NotNull [] heights = this.heights.clone();
    for (int cell = 0; cell < depths.length; cell++) {
      if (body.depths[cell] != EMPTY) {
        depths[cell] = body.depths[cell];
        heights[cell] = body.heights[cell];
      }
    }
    return new PickBuffer(this.size, this.scale, depths, heights);
  }
}
