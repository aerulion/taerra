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

  private static byte[] encode(final int @NotNull [] depths, final int @NotNull [] heights)
      throws Exception {
    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] {'P', 'M', 'P', 'K', 2, 0, 4, 0, 0, 0, 10, 0, 0, 0, 60, 2});
    final @NotNull ByteArrayOutputStream planes = new ByteArrayOutputStream();
    for (final int depth : depths) {
      planes.write(depth >> 8);
      planes.write(depth);
    }
    for (final int height : heights) {
      planes.write(height >> 8);
      planes.write(height);
    }
    try (final @NotNull DeflaterOutputStream compressed = new DeflaterOutputStream(out)) {
      compressed.write(planes.toByteArray());
    }
    return out.toByteArray();
  }

  @Test
  void decodesTheOffsetsFromTheOrigin() throws Exception {
    final @Nullable PickBuffer buffer =
        PickBuffer.decode(encode(new int[] {0, 1, 0xFFFF, 3}, new int[] {5, 6, 0, 8}));
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
