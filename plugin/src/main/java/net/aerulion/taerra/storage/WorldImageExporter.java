package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldImageExporter {

  static final byte @NotNull [] IDAT = "IDAT".getBytes(StandardCharsets.US_ASCII);
  private static final byte @NotNull [] SIGNATURE = {
    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
  };
  private static final byte @NotNull [] IHDR = "IHDR".getBytes(StandardCharsets.US_ASCII);
  private static final byte @NotNull [] IEND = "IEND".getBytes(StandardCharsets.US_ASCII);
  private static final byte FILTER_SUB = 1;
  private static final long MAX_STRIP_PIXELS = 32L << 20;

  private WorldImageExporter() {
    super();
  }

  public static @Nullable WorldImageExport export(
      final @NotNull TileStorage storage,
      final @NotNull TileCoordinate layer,
      final @NotNull Path target)
      throws IOException {
    final @NotNull TileCoordinate origin = layer.at(layer.zoom(), 0, 0);
    final @NotNull Set<Long> present = new HashSet<>();
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (final @NotNull TileCoordinate tile : storage.tilesAt(origin)) {
      present.add(key(tile.x(), tile.z()));
      minX = Math.min(minX, tile.x());
      maxX = Math.max(maxX, tile.x());
      minZ = Math.min(minZ, tile.z());
      maxZ = Math.max(maxZ, tile.z());
    }
    if (present.isEmpty()) {
      return null;
    }

    final int originX = minX;
    final int originZ = minZ;
    final int size = origin.view().tilePixels(origin.zoom());
    if ((long) (maxX - minX + 1) * size * size > MAX_STRIP_PIXELS) {
      throw new IOException(
          "The image is too wide to export at zoom " + origin.zoom() + ", use a higher zoom");
    }
    write(
        target,
        size,
        maxX - minX + 1,
        maxZ - minZ + 1,
        (tileX, tileZ) -> {
          final int x = originX + tileX;
          final int z = originZ + tileZ;
          return present.contains(key(x, z))
              ? storage.loadTile(origin.at(origin.zoom(), x, z)).orElse(null)
              : null;
        });
    return new WorldImageExport(
        target,
        (maxX - minX + 1) * size,
        (maxZ - minZ + 1) * size,
        present.size(),
        Files.size(target));
  }

  static void write(
      final @NotNull Path target,
      final int size,
      final int tilesX,
      final int tilesZ,
      final @NotNull ExportTileSource tiles)
      throws IOException {
    final int width = Math.multiplyExact(tilesX, size);
    final int height = Math.multiplyExact(tilesZ, size);
    final int @NotNull [] strip = new int[Math.multiplyExact(width, size)];
    final byte @NotNull [] row = new byte[Math.addExact(Math.multiplyExact(width, 4), 1)];
    row[0] = FILTER_SUB;

    final @NotNull Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    final @NotNull Deflater deflater = new Deflater();
    try (final @NotNull OutputStream file =
            new BufferedOutputStream(Files.newOutputStream(temp), 1 << 16)) {
      file.write(SIGNATURE);
      writeChunk(
          file,
          IHDR,
          new byte[] {
            (byte) (width >>> 24),
            (byte) (width >>> 16),
            (byte) (width >>> 8),
            (byte) width,
            (byte) (height >>> 24),
            (byte) (height >>> 16),
            (byte) (height >>> 8),
            (byte) height,
            8,
            6,
            0,
            0,
            0
          },
          0,
          13);
      try (final @NotNull DeflaterOutputStream idat =
              new DeflaterOutputStream(new IdatChunkOutputStream(file), deflater, 1 << 16)) {
        for (int tileZ = 0; tileZ < tilesZ; tileZ++) {
          Arrays.fill(strip, 0);
          for (int tileX = 0; tileX < tilesX; tileX++) {
            final @Nullable BufferedImage tile = tiles.tile(tileX, tileZ);
            if (tile != null) {
              tile.getRGB(0, 0, size, size, strip, tileX * size, width);
            }
          }
          for (int y = 0; y < size; y++) {
            encodeRow(strip, y * width, width, row);
            idat.write(row);
          }
        }
      }
      writeChunk(file, IEND, new byte[0], 0, 0);
    } catch (final IOException | RuntimeException e) {
      Files.deleteIfExists(temp);
      throw e;
    } finally {
      deflater.end();
    }
    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void encodeRow(
      final int @NotNull [] pixels, final int offset, final int width, final byte @NotNull [] row) {
    int previousR = 0;
    int previousG = 0;
    int previousB = 0;
    int previousA = 0;
    int out = 1;
    for (int x = 0; x < width; x++) {
      final int argb = pixels[offset + x];
      final int r = argb >>> 16 & 0xFF;
      final int g = argb >>> 8 & 0xFF;
      final int b = argb & 0xFF;
      final int a = argb >>> 24;
      row[out] = (byte) (r - previousR);
      out++;
      row[out] = (byte) (g - previousG);
      out++;
      row[out] = (byte) (b - previousB);
      out++;
      row[out] = (byte) (a - previousA);
      out++;
      previousR = r;
      previousG = g;
      previousB = b;
      previousA = a;
    }
  }

  static void writeChunk(
      final @NotNull OutputStream out,
      final byte @NotNull [] type,
      final byte @NotNull [] data,
      final int offset,
      final int length)
      throws IOException {
    writeInt(out, length);
    out.write(type);
    out.write(data, offset, length);
    final @NotNull CRC32 crc = new CRC32();
    crc.update(type);
    crc.update(data, offset, length);
    writeInt(out, (int) crc.getValue());
  }

  private static void writeInt(final @NotNull OutputStream out, final int value)
      throws IOException {
    out.write(value >>> 24);
    out.write(value >>> 16);
    out.write(value >>> 8);
    out.write(value);
  }

  private static long key(final int x, final int z) {
    return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
  }
}
