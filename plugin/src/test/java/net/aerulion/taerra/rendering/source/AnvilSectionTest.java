package net.aerulion.taerra.rendering.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnvilSectionTest {

  @Test
  @DisplayName("palette widths on disk follow the game's rules for blocks and biomes")
  void bitsOnDiskMatchTheGame() {

    assertEquals(0, AnvilSection.bitsOnDisk(1, 4, 8));
    assertEquals(4, AnvilSection.bitsOnDisk(2, 4, 8));
    assertEquals(4, AnvilSection.bitsOnDisk(16, 4, 8));
    assertEquals(5, AnvilSection.bitsOnDisk(17, 4, 8));
    assertEquals(8, AnvilSection.bitsOnDisk(256, 4, 8));
    assertEquals(9, AnvilSection.bitsOnDisk(257, 4, 8));

    assertEquals(1, AnvilSection.bitsOnDisk(2, 1, 3));
    assertEquals(3, AnvilSection.bitsOnDisk(8, 1, 3));
    assertEquals(4, AnvilSection.bitsOnDisk(9, 1, 3));
  }

  @Test
  @DisplayName("unpacking a whole section agrees with reading each entry on its own")
  void unpackAllMatchesSingleReads() {
    final @NotNull Random random = new Random(7);
    for (int bits = 1; bits <= 12; bits++) {
      final long @NotNull [] data = new long[(4096 + 64 / bits - 1) / (64 / bits)];
      for (int i = 0; i < data.length; i++) {
        data[i] = random.nextLong();
      }
      final int paletteSize = Math.max(1, (1 << bits) - 3);
      final short @NotNull [] all = AnvilSection.unpackAll(data, bits, paletteSize);
      for (int index = 0; index < 4096; index++) {
        final int single = AnvilSection.unpack(data, bits, index);
        assertEquals(
            single < paletteSize ? single : 0, all[index], "bits=" + bits + " index=" + index);
      }
    }
  }
}
