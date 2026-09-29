package net.aerulion.taerra.mod.pick;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PickBuffer(int size, int scale, int originDepth, int originHeight, char[] planes) {

  private static final int HEADER_BYTES = 16;
  private static final int FORMAT_VERSION = 2;
  private static final char NO_VALUE = 0xFFFF;

  public static @Nullable PickBuffer decode(final byte @NotNull [] raw) {
    if (raw.length < HEADER_BYTES
        || raw[0] != 'P'
        || raw[1] != 'M'
        || raw[2] != 'P'
        || raw[3] != 'K'
        || raw[4] != FORMAT_VERSION) {
      return null;
    }
    final int size = ((raw[5] & 0xFF) << 8) | (raw[6] & 0xFF);
    final int scale = raw[15] & 0xFF;
    if (scale == 0 || size % scale != 0) {
      return null;
    }
    final int stored = size / scale;
    final byte @NotNull [] planes;
    try (final @NotNull Inflater inflater = new Inflater()) {
      inflater.setInput(raw, HEADER_BYTES, raw.length - HEADER_BYTES);
      final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream(stored * stored * 4);
      final byte @NotNull [] chunk = new byte[8192];
      while (!inflater.finished()) {
        final int read = inflater.inflate(chunk);
        if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          break;
        }
        out.write(chunk, 0, read);
      }
      planes = out.toByteArray();
    } catch (final DataFormatException e) {
      return null;
    }
    if (planes.length < stored * stored * 4) {
      return null;
    }
    final char @NotNull [] values = new char[stored * stored * 2];
    for (int i = 0; i < values.length; i++) {
      values[i] = (char) (((planes[i * 2] & 0xFF) << 8) | (planes[i * 2 + 1] & 0xFF));
    }
    return new PickBuffer(size, scale, readInt(raw, 7), readInt(raw, 11), values);
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
    if (x < 0 || y < 0 || x >= this.size || y >= this.size || this.planes[index] == NO_VALUE) {
      return null;
    }
    return new int[] {
      this.originDepth + this.planes[index],
      this.originHeight + this.planes[stored * stored + index]
    };
  }
}
