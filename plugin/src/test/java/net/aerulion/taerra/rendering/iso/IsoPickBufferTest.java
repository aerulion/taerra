package net.aerulion.taerra.rendering.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Inflater;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsBuilder;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.Lighting;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IsoPickBufferTest {

  private static final int MIN_WORLD_Y = -64;
  private static final int MAX_WORLD_Y = 320;
  private static final int GROUND_Y = 64;
  private static final int SUBJECT_X = 216;
  private static final int SUBJECT_Z = 184;

  private static final int STONE_TOP = 0xFF204080;
  private static final int STONE_SIDE = 0xFF3050A0;
  private static final int PLANK_TOP = 0xFF80C040;
  private static final int PLANK_SIDE = 0xFF90D050;

  private static final @NotNull MapColorResolver COLORS =
      new MapColorResolver() {
        @Override
        public int topColor(
            final @NotNull Material material,
            final @Nullable Biome biome,
            final int x,
            final int z) {
          return material == Material.OAK_PLANKS ? PLANK_TOP : STONE_TOP;
        }

        @Override
        public int sideColor(
            final @NotNull Material material,
            final @Nullable Biome biome,
            final int x,
            final int z) {
          return material == Material.OAK_PLANKS ? PLANK_SIDE : STONE_SIDE;
        }
      };

  private static @NotNull ChunkColumnsSource world(final int raisedTop, final int raisedBottom) {
    final @NotNull Map<Long, ChunkColumns> cache = new HashMap<>();
    return (chunkX, chunkZ) ->
        cache.computeIfAbsent(
            ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL),
            ignored -> {
              final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
              for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                  if (chunkX * 16 + localX == SUBJECT_X && chunkZ * 16 + localZ == SUBJECT_Z) {
                    builder.addRun(raisedTop, raisedBottom, Material.OAK_PLANKS);
                  }
                  builder.addRun(GROUND_Y, 0, Material.STONE);
                  builder.completeColumn(null, null);
                }
              }
              return builder.build();
            });
  }

  private static @NotNull RenderedImage render(
      final @NotNull MapRotation rotation,
      final @NotNull ChunkColumnsSource source,
      final int surfaceY) {
    final @NotNull IsoProjection projection = IsoTileGeometry.projection();
    final int rotatedX = rotation.rotatedX(SUBJECT_X, SUBJECT_Z);
    final int rotatedZ = rotation.rotatedZ(SUBJECT_X, SUBJECT_Z);
    final int tileX = IsoTileGeometry.screenToTileX(projection.screenX(rotatedX, rotatedZ), 0);
    final int tileY =
        IsoTileGeometry.screenToTileY(projection.screenY(rotatedX, surfaceY, rotatedZ), 0);
    return new IsoTileRendererImpl(COLORS)
        .renderTile(rotation, tileX, tileY, MIN_WORLD_Y, MAX_WORLD_Y, source, true);
  }

  private static @NotNull ScreenBounds boundsOf(
      final @NotNull MapRotation rotation, final int surfaceY) {
    final @NotNull IsoProjection projection = IsoTileGeometry.projection();
    final int rotatedX = rotation.rotatedX(SUBJECT_X, SUBJECT_Z);
    final int rotatedZ = rotation.rotatedZ(SUBJECT_X, SUBJECT_Z);
    return IsoTileGeometry.tileBounds(
        IsoTileGeometry.screenToTileX(projection.screenX(rotatedX, rotatedZ), 0),
        IsoTileGeometry.screenToTileY(projection.screenY(rotatedX, surfaceY, rotatedZ), 0),
        0);
  }

  private static byte[] assertNotNullBytes(final byte[] value) {
    assertNotNull(value);
    return value;
  }

  private static boolean cellIsAll(
      final int[] pixels,
      final int size,
      final int scale,
      final int pixelX,
      final int pixelY,
      final int color) {
    final int cellX = pixelX / scale * scale;
    final int cellY = pixelY / scale * scale;
    for (int y = cellY; y < cellY + scale; y++) {
      for (int x = cellX; x < cellX + scale; x++) {
        if (pixels[y * size + x] != color) {
          return false;
        }
      }
    }
    return true;
  }

  @Test
  @DisplayName(
      "a distinctive block resolves back to itself, missing only single pixels on its edges, in every rotation")
  void paintedPixelsResolveToTheBlockThatPaintedThem() throws Exception {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull RenderedImage rendered =
          render(rotation, world(GROUND_Y + 3, GROUND_Y + 1), GROUND_Y + 4);
      final @NotNull ScreenBounds bounds = boundsOf(rotation, GROUND_Y + 4);
      assertNotNull(rendered.pick());
      final byte @Nullable [] encoded = rendered.pick().encode();
      assertNotNull(encoded, "a tile with terrain in it should produce a buffer");
      final @NotNull Decoded decoded = Decoded.of(encoded);

      final int size = rendered.image().getWidth();
      final int[] pixels = rendered.image().getRGB(0, 0, size, size, null, 0, size);
      final int top = Lighting.face(PLANK_TOP, Lighting.TOP, 0, false, Lighting.NO_CONTACT);
      final java.util.@NotNull Set<Integer> wallX = new java.util.HashSet<>();
      for (final int contact :
          new int[] {Lighting.NO_CONTACT, Lighting.CONTACT_NEAR, Lighting.CONTACT_FOOT}) {
        wallX.add(Lighting.face(PLANK_SIDE, Lighting.POSITIVE_X, 0, false, contact));
      }
      int checkedTops = 0;
      int checkedWalls = 0;
      int painted = 0;
      int exact = 0;

      for (int pixelY = 0; pixelY < size; pixelY++) {
        for (int pixelX = 0; pixelX < size; pixelX++) {
          final int color = pixels[pixelY * size + pixelX];
          if (color != top && !wallX.contains(color)) {
            continue;
          }
          painted++;
          final int @Nullable [] block = decoded.resolve(rotation, bounds, pixelX, pixelY);
          assertNotNull(block, "a painted pixel must resolve at " + rotation);
          final boolean exactCell = cellIsAll(pixels, size, decoded.scale(), pixelX, pixelY, color);
          if (block[0] != SUBJECT_X || block[2] != SUBJECT_Z) {
            assertFalse(
                exactCell,
                "a cell inside the block resolved elsewhere at "
                    + pixelX
                    + ","
                    + pixelY
                    + " in "
                    + rotation);
            continue;
          }
          exact++;
          if (color == top) {
            assertEquals(GROUND_Y + 3, block[1], "a top face should report the block it caps");
            checkedTops++;
          } else {
            assertTrue(
                block[1] >= GROUND_Y + 1 && block[1] <= GROUND_Y + 3,
                "a wall pixel should report one of the wall's own levels, got " + block[1]);
            checkedWalls++;
          }
        }
      }
      assertTrue(exact * 2 >= painted, "most of the block should resolve exactly at " + rotation);
      assertTrue(checkedTops > 0, "the subject's top face should be visible at " + rotation);
      assertTrue(checkedWalls > 0, "the subject's wall should be visible at " + rotation);
    }
  }

  @Test
  @DisplayName("flat ground resolves to the column under every single pixel")
  void flatGroundResolvesEverywhere() throws Exception {
    final @NotNull MapRotation rotation = MapRotation.R90;
    final @NotNull RenderedImage rendered =
        render(rotation, world(GROUND_Y, GROUND_Y), GROUND_Y + 1);
    final @NotNull ScreenBounds bounds = boundsOf(rotation, GROUND_Y + 1);
    final @NotNull Decoded decoded = Decoded.of(assertNotNullBytes(rendered.pick().encode()));

    final int size = rendered.image().getWidth();
    for (int pixelY = 0; pixelY < size; pixelY++) {
      for (int pixelX = 0; pixelX < size; pixelX++) {
        final int @Nullable [] block = decoded.resolve(rotation, bounds, pixelX, pixelY);
        assertNotNull(block, "flat ground fills the tile, so every pixel must resolve");
        assertEquals(GROUND_Y, block[1]);

        final @NotNull IsoProjection projection = IsoTileGeometry.projection();
        final int rotatedX = rotation.rotatedX(block[0], block[2]);
        final int rotatedZ = rotation.rotatedZ(block[0], block[2]);
        final long column = projection.screenX(rotatedX, rotatedZ) - bounds.minX();
        assertTrue(
            Math.abs(column - pixelX) <= projection.halfWidth(),
            "resolved block projects to column " + column + ", not near " + pixelX);
      }
    }
  }

  @Test
  @DisplayName("a tile with nothing in it produces no buffer rather than an empty one")
  void emptyTileProducesNoBuffer() throws Exception {
    final @NotNull RenderedImage rendered =
        new IsoTileRendererImpl(COLORS)
            .renderTile(
                MapRotation.R0, 0, 0, MIN_WORLD_Y, MAX_WORLD_Y, (chunkX, chunkZ) -> null, true);
    assertNotNull(rendered.pick());
    assertNull(rendered.pick().encode());
  }

  @Test
  @DisplayName("a buffer is refused rather than wrapped when its values do not fit")
  void oversizedSpanIsRefused() throws Exception {
    final int size = 4;
    final int @NotNull [] depth = new int[size * size];
    final int @NotNull [] height = new int[size * size];
    Arrays.fill(depth, PickBuffer.UNPAINTED);
    depth[0] = 0;
    depth[1] = 0x20000;
    assertNull(new PickBuffer(size, depth, height).encode());

    depth[1] = PickBuffer.NO_VALUE - 1;
    assertNotNull(
        new PickBuffer(size, depth, height).encode(), "a span that just fits should be kept");
  }

  @Test
  @DisplayName("no pick buffer is produced when none was asked for")
  void pickIsOptional() {
    assertNotNull(
        render(MapRotation.R0, world(GROUND_Y, GROUND_Y), GROUND_Y + 1).pick(),
        "the other tests ask for one and must get it");
    final @NotNull RenderedImage without =
        new IsoTileRendererImpl(COLORS)
            .renderTile(
                MapRotation.R0, 0, 0, MIN_WORLD_Y, MAX_WORLD_Y, world(GROUND_Y, GROUND_Y), false);
    assertNull(without.pick());
  }

  private record Decoded(int size, int scale, int originDepth, int originHeight, int[] values) {

    static @NotNull Decoded of(final byte @NotNull [] encoded) throws Exception {
      assertEquals('P', encoded[0]);
      assertEquals('M', encoded[1]);
      assertEquals('P', encoded[2]);
      assertEquals('K', encoded[3]);
      assertEquals(PickBuffer.FORMAT_VERSION, encoded[4]);
      final int size = ((encoded[5] & 0xFF) << 8) | (encoded[6] & 0xFF);
      final int originDepth = readInt(encoded, 7);
      final int originHeight = readInt(encoded, 11);
      final int scale = encoded[15];
      final int stored = size / scale;

      final @NotNull Inflater inflater = new Inflater();
      inflater.setInput(encoded, PickBuffer.HEADER_BYTES, encoded.length - PickBuffer.HEADER_BYTES);
      final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
      final byte @NotNull [] chunk = new byte[8192];
      while (!inflater.finished()) {
        final int read = inflater.inflate(chunk);
        if (read == 0 && inflater.needsInput()) {
          break;
        }
        out.write(chunk, 0, read);
      }
      inflater.end();

      final byte @NotNull [] planes = out.toByteArray();
      assertEquals(stored * stored * 4, planes.length, "both planes should survive the round trip");
      final int @NotNull [] values = new int[stored * stored * 2];
      for (int index = 0; index < values.length; index++) {
        values[index] = ((planes[index * 2] & 0xFF) << 8) | (planes[index * 2 + 1] & 0xFF);
      }
      return new Decoded(size, scale, originDepth, originHeight, values);
    }

    private static int readInt(final byte @NotNull [] data, final int offset) {
      return ((data[offset] & 0xFF) << 24)
          | ((data[offset + 1] & 0xFF) << 16)
          | ((data[offset + 2] & 0xFF) << 8)
          | (data[offset + 3] & 0xFF);
    }

    int @Nullable [] resolve(
        final @NotNull MapRotation rotation,
        final @NotNull ScreenBounds bounds,
        final int pixelX,
        final int pixelY) {
      final int stored = this.size / this.scale;
      final int index = pixelY / this.scale * stored + pixelX / this.scale;
      if (this.values[index] == PickBuffer.NO_VALUE) {
        return null;
      }
      final @NotNull IsoProjection projection = IsoTileGeometry.projection();
      final int half = projection.halfWidth();
      final int depth = this.originDepth + this.values[index];
      final int blockY = this.originHeight + this.values[stored * stored + index];
      final long screenX = bounds.minX() + pixelX;

      final int higher = Math.toIntExact(Math.floorDiv(screenX + half, half));
      final int difference = ((higher ^ depth) & 1) == 0 ? higher : higher - 1;
      final int rotatedX = (depth + difference) / 2;
      final int rotatedZ = (depth - difference) / 2;
      return new int[] {
        rotation.worldX(rotatedX, rotatedZ), blockY, rotation.worldZ(rotatedX, rotatedZ)
      };
    }
  }
}
