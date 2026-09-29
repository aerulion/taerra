package net.aerulion.taerra.util;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ImageUtils {

  private static final byte @NotNull [] PNG_SIGNATURE = {
    (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
  };

  private static final int PNG_DEFLATE_LEVEL = 4;

  private ImageUtils() {
    super();
  }

  public static @NotNull BufferedImage createEmptyTile(final int size) {
    return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
  }

  public static @NotNull BufferedImage fromPixels(final int size, final int @NotNull [] argb) {
    final @NotNull BufferedImage image = createEmptyTile(size);
    System.arraycopy(argb, 0, argbPixels(image), 0, size * size);
    return image;
  }

  private static int @NotNull [] argbPixels(final @NotNull BufferedImage image) {
    return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
  }

  public static boolean hasVisiblePixels(final @NotNull BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
      for (final int pixel : argbPixels(image)) {
        if ((pixel >>> 24) != 0) {
          return true;
        }
      }
      return false;
    }
    final int width = image.getWidth();
    final int height = image.getHeight();
    final int @NotNull [] row = new int[width];
    for (int y = 0; y < height; y++) {
      image.getRGB(0, y, width, 1, row, 0, width);
      for (final int pixel : row) {
        if ((pixel >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  public static byte @NotNull [] encodePNG(final @NotNull BufferedImage image) throws IOException {
    if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
      final byte @Nullable [] indexed =
          encodeIndexedPNG(argbPixels(image), image.getWidth(), image.getHeight());
      if (indexed != null) {
        return indexed;
      }
    }
    final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
    if (!writers.hasNext()) {
      throw new IOException("No PNG writer found");
    }

    final ImageWriter writer = writers.next();
    final ImageWriteParam writeParam = writer.getDefaultWriteParam();
    final @NotNull ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);

    try (final @NotNull ImageOutputStream output = new MemoryCacheImageOutputStream(buffer)) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), writeParam);
    } finally {
      writer.dispose();
    }

    return buffer.toByteArray();
  }

  static byte @Nullable [] encodeIndexedPNG(
      final int @NotNull [] argb, final int width, final int height) {
    final @NotNull Int2IntOpenHashMap indexOf = new Int2IntOpenHashMap();
    indexOf.defaultReturnValue(-1);
    final int @NotNull [] palette = new int[256];
    int colors = 0;
    boolean translucent = false;
    final byte @NotNull [] scanlines = new byte[height * (width + 1)];
    int at = 0;
    int lastPixel = 0;
    int lastIndex = -1;
    for (int y = 0; y < height; y++) {

      scanlines[at] = 0;
      at++;
      for (int x = 0; x < width; x++) {
        final int pixel = argb[y * width + x];
        if (pixel != lastPixel || lastIndex < 0) {
          int index = indexOf.get(pixel);
          if (index < 0) {
            if (colors == palette.length) {
              return null;
            }
            index = colors;
            colors++;
            palette[index] = pixel;
            indexOf.put(pixel, index);
            translucent |= (pixel >>> 24) != 0xFF;
          }
          lastPixel = pixel;
          lastIndex = index;
        }
        scanlines[at] = (byte) lastIndex;
        at++;
      }
    }

    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
    out.writeBytes(PNG_SIGNATURE);
    final byte @NotNull [] header = new byte[13];
    putInt(header, 0, width);
    putInt(header, 4, height);
    header[8] = 8;
    header[9] = 3;
    writeChunk(out, "IHDR", header);
    final byte @NotNull [] rgb = new byte[colors * 3];
    final byte @NotNull [] alpha = new byte[colors];
    for (int i = 0; i < colors; i++) {
      rgb[i * 3] = (byte) (palette[i] >> 16);
      rgb[i * 3 + 1] = (byte) (palette[i] >> 8);
      rgb[i * 3 + 2] = (byte) palette[i];
      alpha[i] = (byte) (palette[i] >>> 24);
    }
    writeChunk(out, "PLTE", rgb);
    if (translucent) {
      writeChunk(out, "tRNS", alpha);
    }
    final @NotNull Deflater deflater = new Deflater(PNG_DEFLATE_LEVEL);
    try {
      deflater.setInput(scanlines);
      deflater.finish();
      final @NotNull ByteArrayOutputStream compressed =
          new ByteArrayOutputStream(scanlines.length / 4);
      final byte @NotNull [] buffer = new byte[16384];
      while (!deflater.finished()) {
        compressed.write(buffer, 0, deflater.deflate(buffer));
      }
      writeChunk(out, "IDAT", compressed.toByteArray());
    } finally {
      deflater.end();
    }
    writeChunk(out, "IEND", new byte[0]);
    return out.toByteArray();
  }

  private static void writeChunk(
      final @NotNull ByteArrayOutputStream out,
      final @NotNull String type,
      final byte @NotNull [] data) {
    final byte @NotNull [] lengthAndType = new byte[8];
    putInt(lengthAndType, 0, data.length);
    for (int i = 0; i < 4; i++) {
      lengthAndType[4 + i] = (byte) type.charAt(i);
    }
    out.writeBytes(lengthAndType);
    out.writeBytes(data);
    final @NotNull CRC32 crc = new CRC32();
    crc.update(lengthAndType, 4, 4);
    crc.update(data);
    final byte @NotNull [] checksum = new byte[4];
    putInt(checksum, 0, (int) crc.getValue());
    out.writeBytes(checksum);
  }

  private static void putInt(final byte @NotNull [] into, final int offset, final int value) {
    into[offset] = (byte) (value >>> 24);
    into[offset + 1] = (byte) (value >>> 16);
    into[offset + 2] = (byte) (value >>> 8);
    into[offset + 3] = (byte) value;
  }

  public static @Nullable BufferedImage loadPNG(final @NotNull File file) throws IOException {
    if (!file.exists()) {
      return null;
    }
    return ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
  }

  public static @NotNull BufferedImage combineTiles(
      final @Nullable BufferedImage topLeft,
      final @Nullable BufferedImage topRight,
      final @Nullable BufferedImage bottomLeft,
      final @Nullable BufferedImage bottomRight,
      final int tileSize) {
    final int half = tileSize / 2;
    final int @NotNull [] combined = new int[tileSize * tileSize];

    downsampleInto(combined, tileSize, 0, 0, topLeft);
    downsampleInto(combined, tileSize, half, 0, topRight);
    downsampleInto(combined, tileSize, 0, half, bottomLeft);
    downsampleInto(combined, tileSize, half, half, bottomRight);

    return fromPixels(tileSize, combined);
  }

  private static void downsampleInto(
      final int[] target,
      final int targetStride,
      final int targetX,
      final int targetY,
      final @Nullable BufferedImage source) {
    if (source == null) {
      return;
    }

    final int half = targetStride / 2;
    final int sourceWidth = source.getWidth();
    final int sourceHeight = source.getHeight();
    final int @NotNull [] columns = new int[half];
    for (int x = 0; x < half; x++) {
      columns[x] = x * sourceWidth / half;
    }

    if (source.getType() == BufferedImage.TYPE_BYTE_INDEXED
        && source.getColorModel() instanceof final @NotNull IndexColorModel indexed) {
      final int @NotNull [] colors = new int[indexed.getMapSize()];
      indexed.getRGBs(colors);
      final byte[] indices = ((DataBufferByte) source.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < half; y++) {
        final int sourceRow = y * sourceHeight / half * sourceWidth;
        final int targetRow = (targetY + y) * targetStride + targetX;
        for (int x = 0; x < half; x++) {
          target[targetRow + x] = colors[indices[sourceRow + columns[x]] & 0xFF];
        }
      }
      return;
    }
    if (source.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
      final byte[] bytes = ((DataBufferByte) source.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < half; y++) {
        final int sourceRow = y * sourceHeight / half * sourceWidth;
        final int targetRow = (targetY + y) * targetStride + targetX;
        for (int x = 0; x < half; x++) {
          final int at = (sourceRow + columns[x]) * 4;
          target[targetRow + x] =
              (bytes[at] & 0xFF) << 24
                  | (bytes[at + 3] & 0xFF) << 16
                  | (bytes[at + 2] & 0xFF) << 8
                  | (bytes[at + 1] & 0xFF);
        }
      }
      return;
    }
    final int[] sourcePixels =
        source.getType() == BufferedImage.TYPE_INT_ARGB
            ? argbPixels(source)
            : source.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
    for (int y = 0; y < half; y++) {
      final int sourceRow = y * sourceHeight / half * sourceWidth;
      final int targetRow = (targetY + y) * targetStride + targetX;
      for (int x = 0; x < half; x++) {
        target[targetRow + x] = sourcePixels[sourceRow + columns[x]];
      }
    }
  }
}
