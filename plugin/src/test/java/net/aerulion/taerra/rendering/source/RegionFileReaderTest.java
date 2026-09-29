package net.aerulion.taerra.rendering.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileReaderTest {

  private static final int SECTOR = 4096;
  private static final byte ZLIB = 2;

  private static @NotNull Path region(
      final @NotNull Path directory, final int headerEntry, final byte @NotNull [] sector2)
      throws Exception {
    final @NotNull ByteBuffer file = ByteBuffer.allocate(3 * SECTOR);
    file.putInt(0, headerEntry);
    file.put(2 * SECTOR, sector2);
    final @NotNull Path path = directory.resolve("r.0.0.mca");
    Files.write(path, file.array());
    return path;
  }

  @Test
  @DisplayName("a stored chunk reads back as written")
  void readsStoredChunk(@TempDir final @NotNull Path directory) throws Exception {
    final @NotNull CompoundTag chunk = new CompoundTag();
    chunk.putInt("xPos", 3);
    final @NotNull ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (final @NotNull DataOutputStream out =
            new DataOutputStream(new DeflaterOutputStream(compressed))) {
      NbtIo.write(chunk, out);
    }
    final @NotNull ByteBuffer sector = ByteBuffer.allocate(SECTOR);
    sector.putInt(compressed.size() + 1).put(ZLIB).put(compressed.toByteArray());

    try (final @NotNull RegionFileReader reader =
                RegionFileReader.open(region(directory, 2 << 8 | 1, sector.array()));
        final @Nullable DataInputStream input = reader.chunkStream(0, 0)) {
      assertNotNull(input);
      assertEquals(chunk, NbtIo.read(input));
    }
  }

  @Test
  @DisplayName("a torn length never allocates past the end of the file")
  void tornLengthIsBounded(@TempDir final @NotNull Path directory) throws Exception {
    final @NotNull ByteBuffer sector = ByteBuffer.allocate(SECTOR);
    sector.putInt(1_500_000_000).put(ZLIB);

    try (final @NotNull RegionFileReader reader =
            RegionFileReader.open(region(directory, 2 << 8 | 255, sector.array()))) {
      assertNull(reader.chunkStream(0, 0));
    }
  }

  @Test
  @DisplayName("only chunks the generator placed features in are drawn")
  void skipsUnfinishedChunks() {
    assertTrue(RegionFileChunkSourceProvider.isFinished("minecraft:full"));
    assertTrue(RegionFileChunkSourceProvider.isFinished("minecraft:features"));
    assertTrue(RegionFileChunkSourceProvider.isFinished(""));
    assertFalse(RegionFileChunkSourceProvider.isFinished("minecraft:noise"));
    assertFalse(RegionFileChunkSourceProvider.isFinished("minecraft:carvers"));
  }
}
