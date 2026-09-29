package net.aerulion.taerra.rendering.source;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RegionFileReader implements AutoCloseable {

  private static final int SECTOR_BYTES = 4096;
  private static final int EXTERNAL_STREAM_FLAG = 0x80;

  private final @NotNull Path path;
  private final @NotNull FileChannel channel;
  private final int @NotNull [] offsets;

  private RegionFileReader(
      final @NotNull Path path, final @NotNull FileChannel channel, final int @NotNull [] offsets) {
    super();
    this.path = path;
    this.channel = channel;
    this.offsets = offsets;
  }

  static @NotNull RegionFileReader open(final @NotNull Path path) throws IOException {
    final @NotNull FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
    final @NotNull ByteBuffer header = ByteBuffer.allocate(SECTOR_BYTES);
    readFully(channel, header, 0L);
    final int @NotNull [] offsets = new int[SECTOR_BYTES / 4];
    header.asIntBuffer().get(offsets, 0, Math.min(offsets.length, header.limit() / 4));
    return new RegionFileReader(path, channel, offsets);
  }

  private static void readFully(
      final @NotNull FileChannel channel, final @NotNull ByteBuffer buffer, final long position)
      throws IOException {
    long at = position;
    while (buffer.hasRemaining()) {
      final int read = channel.read(buffer, at);
      if (read < 0) {
        break;
      }
      at += read;
    }
    buffer.flip();
  }

  @Nullable
  DataInputStream chunkStream(final int chunkX, final int chunkZ) throws IOException {
    final int offset = this.offsets[(chunkX & 31) + (chunkZ & 31) * 32];
    if (offset == 0) {
      return null;
    }
    final long position = (long) (offset >>> 8) * SECTOR_BYTES;
    final long available = this.channel.size() - position;
    long bytes = (long) (offset & 0xFF) * SECTOR_BYTES;
    if ((offset & 0xFF) == 255) {
      final @NotNull ByteBuffer length = ByteBuffer.allocate(4);
      readFully(this.channel, length, position);
      bytes = length.remaining() < 4 ? 0L : length.getInt(0) + 4L;
    }
    bytes = Math.min(bytes, available);
    if (bytes < 5L) {
      return null;
    }
    final @NotNull ByteBuffer buffer = ByteBuffer.allocate((int) bytes);
    readFully(this.channel, buffer, position);
    if (buffer.remaining() < 5) {
      return null;
    }
    final int length = buffer.getInt();
    final int versionId = buffer.get() & 0xFF;
    final @NotNull InputStream raw;
    if ((versionId & EXTERNAL_STREAM_FLAG) != 0) {
      final @NotNull Path external =
          this.path.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
      if (!Files.isRegularFile(external)) {
        return null;
      }
      raw = Files.newInputStream(external);
    } else {
      if (length <= 1 || length - 1 > buffer.remaining()) {
        return null;
      }
      raw = new ByteArrayInputStream(buffer.array(), buffer.position(), length - 1);
    }
    final @Nullable RegionFileVersion version =
        RegionFileVersion.fromId(versionId & ~EXTERNAL_STREAM_FLAG);
    if (version == null || version == RegionFileVersion.VERSION_CUSTOM) {
      raw.close();
      return null;
    }
    return new DataInputStream(version.wrap(raw));
  }

  @Override
  public void close() {
    try {
      this.channel.close();
    } catch (final IOException ignored) {

    }
  }
}
