package net.aerulion.taerra.mod.pick;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PickBufferTest {

  private static byte[] encode() throws Exception {
    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(
        new byte[] {
          'P',
          'M',
          'P',
          'K',
          3,
          0,
          4,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0x85,
          0,
          0,
          0,
          60,
          2,
          2,
          1
        });
    try (final @NotNull DeflaterOutputStream compressed = new DeflaterOutputStream(out)) {
      compressed.write(new byte[] {0x20, 3, 2, 0, (byte) 0xFE, 5, 1, 0, 8});
    }
    return out.toByteArray();
  }

  @Test
  void decodesTheOffsetsFromTheOrigin() throws Exception {
    final @Nullable PickBuffer buffer = PickBuffer.decode(encode());
    assertNotNull(buffer);
    assertArrayEquals(new int[] {10, 65}, buffer.cell(0, 1));
    assertArrayEquals(new int[] {11, 66}, buffer.cell(3, 0));
    assertArrayEquals(new int[] {13, 68}, buffer.cell(2, 2));
    assertNull(buffer.cell(0, 2), "an unpainted pixel");
    assertNull(buffer.cell(4, 0), "outside the tile");
  }

  @Test
  void rejectsAnythingElse() {
    assertNull(PickBuffer.decode(new byte[] {'P', 'N', 'G'}));
  }

  @Test
  void everyPixelOfATopFaceFindsItsBlock() {
    final int x = -37;
    final int z = 212;
    final int @NotNull [][] rotated = {{x, z}, {z, -x - 1}, {-x - 1, -z - 1}, {-z - 1, x}};
    for (int rotation = 0; rotation < 4; rotation++) {
      final int rotatedX = rotated[rotation][0];
      final int rotatedZ = rotated[rotation][1];
      for (int offset = -4; offset < 4; offset++) {
        assertArrayEquals(
            new int[] {x, 70, z},
            PickBuffer.isometricBlock(
                rotation, 8, (rotatedX - rotatedZ) * 4 + offset, rotatedX + rotatedZ, 70),
            "rotation " + rotation + ", column " + offset);
      }
    }
  }
}
