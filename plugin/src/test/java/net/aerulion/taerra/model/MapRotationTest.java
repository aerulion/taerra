package net.aerulion.taerra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapRotationTest {

  private static final int @NotNull [] SAMPLES = {-33, -17, -16, -1, 0, 1, 15, 16, 200};

  @Test
  @DisplayName(
      "identifiers round-trip, so a stored tile path names the rotation it was written from")
  void identifiersRoundTrip() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      assertSame(rotation, MapRotation.fromId(rotation.id(), MapRotation.R0));
      assertSame(rotation, MapRotation.fromIndex(rotation.index(), MapRotation.R0));
    }
    assertSame(MapRotation.R180, MapRotation.fromId(null, MapRotation.R180));
    assertSame(MapRotation.R180, MapRotation.fromId("not a number", MapRotation.R180));
    assertSame(MapRotation.R180, MapRotation.fromId("9", MapRotation.R180));
    assertNull(MapRotation.ofIndex(-1));
    assertNull(MapRotation.ofIndex(MapRotation.COUNT));
  }

  @Test
  @DisplayName("rotating and rotating back is the identity, for every rotation and every cell")
  void rotationRoundTrips() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      for (final int x : SAMPLES) {
        for (final int z : SAMPLES) {
          final int rotatedX = rotation.rotatedX(x, z);
          final int rotatedZ = rotation.rotatedZ(x, z);
          assertEquals(x, rotation.worldX(rotatedX, rotatedZ), "x lost at " + rotation);
          assertEquals(z, rotation.worldZ(rotatedX, rotatedZ), "z lost at " + rotation);
          assertEquals(rotatedX, rotation.rotatedX((long) x, z), "long and int forms disagree");
          assertEquals(rotatedZ, rotation.rotatedZ((long) x, z), "long and int forms disagree");
        }
      }
    }
  }

  @Test
  @DisplayName("each rotation is a quarter turn further than the last")
  void eachRotationIsOneMoreQuarterTurn() {
    for (final int x : SAMPLES) {
      for (final int z : SAMPLES) {
        int turnedX = x;
        int turnedZ = z;
        for (final @NotNull MapRotation rotation : MapRotation.values()) {
          assertEquals(
              rotation.rotatedX(x, z),
              turnedX,
              "composing quarter turns disagrees with " + rotation);
          assertEquals(
              rotation.rotatedZ(x, z),
              turnedZ,
              "composing quarter turns disagrees with " + rotation);
          final int nextX = MapRotation.R90.rotatedX(turnedX, turnedZ);
          turnedZ = MapRotation.R90.rotatedZ(turnedX, turnedZ);
          turnedX = nextX;
        }
        assertEquals(x, turnedX, "four quarter turns should be the identity");
        assertEquals(z, turnedZ, "four quarter turns should be the identity");
      }
    }
  }

  @Test
  @DisplayName("a chunk rotates onto a chunk, so a rotated pyramid still reads whole chunks")
  void chunksRotateOntoChunks() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      for (final int chunkX : new int[] {-3, -1, 0, 2, 17}) {
        for (final int chunkZ : new int[] {-3, -1, 0, 2, 17}) {
          final int rotatedChunkX = rotation.rotatedX(chunkX, chunkZ);
          final int rotatedChunkZ = rotation.rotatedZ(chunkX, chunkZ);
          for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

              final int rotatedBlockX = rotatedChunkX * 16 + localX;
              final int rotatedBlockZ = rotatedChunkZ * 16 + localZ;
              final int worldX = chunkX * 16 + rotation.localX(localX, localZ);
              final int worldZ = chunkZ * 16 + rotation.localZ(localX, localZ);
              assertEquals(
                  rotatedBlockX,
                  rotation.rotatedX(worldX, worldZ),
                  "chunk-local x diverges at " + rotation);
              assertEquals(
                  rotatedBlockZ,
                  rotation.rotatedZ(worldX, worldZ),
                  "chunk-local z diverges at " + rotation);
            }
          }
        }
      }
    }
  }

  @Test
  @DisplayName(
      "local coordinates are a permutation of the sixteen by sixteen grid, so no column is read twice")
  void localCoordinatesArePermutations() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull Set<Integer> seen = new HashSet<>();
      for (int localX = 0; localX < 16; localX++) {
        for (int localZ = 0; localZ < 16; localZ++) {
          assertTrue(
              seen.add((rotation.localX(localX, localZ) << 4) | rotation.localZ(localX, localZ)),
              "two rotated positions map to one column at " + rotation);
        }
      }
      assertEquals(256, seen.size());
    }
  }

  @Test
  @DisplayName(
      "directions turn without the cell offset, so the sun keeps its length in every rotation")
  void directionsTurnWithoutOffset() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      for (final int deltaX : new int[] {-809, -1, 0, 1, 627}) {
        for (final int deltaZ : new int[] {-627, -1, 0, 1, 809}) {
          final int worldDeltaX = rotation.worldDeltaX(deltaX, deltaZ);
          final int worldDeltaZ = rotation.worldDeltaZ(deltaX, deltaZ);
          assertEquals(
              Math.abs(deltaX) + Math.abs(deltaZ),
              Math.abs(worldDeltaX) + Math.abs(worldDeltaZ),
              "a direction changed length at " + rotation);

          assertEquals(
              deltaX,
              rotation.rotatedX(worldDeltaX, worldDeltaZ) - rotation.rotatedX(0, 0),
              "direction x diverges at " + rotation);
          assertEquals(
              deltaZ,
              rotation.rotatedZ(worldDeltaX, worldDeltaZ) - rotation.rotatedZ(0, 0),
              "direction z diverges at " + rotation);
        }
      }
    }
  }
}
