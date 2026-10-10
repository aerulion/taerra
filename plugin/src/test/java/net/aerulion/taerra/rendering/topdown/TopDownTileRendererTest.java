package net.aerulion.taerra.rendering.topdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.aerulion.taerra.rendering.color.BlockColors;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsBuilder;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.Lighting;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopDownTileRendererTest {

  private static final int TILE_SIZE = 16;
  private static final int GROUND_Y = 64;

  private static final int STONE_TOP = 0xFF808080;
  private static final int STONE_SIDE = 0xFF606060;
  private static final int GRASS_TOP = 0xFF40A040;
  private static final int WATER_TOP = 0xFF3050C0;
  private static final int TORCH_TOP = 0xFFFFE000;

  private static final @NotNull MapColorResolver COLORS =
      blockType ->
          new BlockColors() {
            @Override
            public int topColor(final @Nullable Biome biome, final int x, final int z) {
              return blockType == BlockType.GRASS_BLOCK
                  ? GRASS_TOP
                  : blockType == BlockType.WATER
                      ? WATER_TOP
                      : blockType == BlockType.TORCH ? TORCH_TOP : STONE_TOP;
            }

            @Override
            public int sideColor(final @Nullable Biome biome, final int x, final int z) {
              return STONE_SIDE;
            }
          };

  private static int lit(final int topColor) {
    return Lighting.face(topColor, Lighting.TOP, 0, false, Lighting.NO_CONTACT);
  }

  private static @NotNull ChunkColumnsSource world(final @NotNull ColumnBuilder builder) {
    final @NotNull Map<Long, ChunkColumns> cache = new HashMap<>();
    return (chunkX, chunkZ) ->
        cache.computeIfAbsent(
            ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL),
            ignored -> {
              final @NotNull ChunkColumnsBuilder chunk = ChunkColumns.builder(chunkX, chunkZ);
              for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                  builder.build(chunk, chunkX * 16 + localX, chunkZ * 16 + localZ);
                }
              }
              return chunk.build();
            });
  }

  private static @NotNull ChunkColumnsSource flatGrass() {
    return world(
        (chunk, x, z) -> {
          chunk.addRun(GROUND_Y, GROUND_Y, BlockType.GRASS_BLOCK);
          chunk.addRun(GROUND_Y - 1, 0, BlockType.STONE);
          chunk.completeColumn(null, null);
        });
  }

  private static int[] render(final @NotNull ChunkColumnsSource source) {
    final @NotNull BufferedImage image =
        new TopDownTileRendererImpl(COLORS).render(source, 0, 0, TILE_SIZE).image();
    return image.getRGB(0, 0, TILE_SIZE, TILE_SIZE, null, 0, TILE_SIZE);
  }

  private static @NotNull ChunkColumnsSource flatWater(final int depth) {
    return world(
        (chunk, x, z) -> {
          chunk.addRun(GROUND_Y, GROUND_Y - depth + 1, BlockType.WATER);
          chunk.addRun(GROUND_Y - depth, 0, BlockType.STONE);
          chunk.completeColumn(null, null);
        });
  }

  private static @NotNull ChunkColumnsSource stepDownAt(final int stepX, final int drop) {
    return world(
        (chunk, x, z) -> {
          chunk.addRun(x > stepX ? GROUND_Y - drop : GROUND_Y, 0, BlockType.GRASS_BLOCK);
          chunk.completeColumn(null, null);
        });
  }

  private static double brightness(final int argb) {
    return 0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF);
  }

  @Test
  @DisplayName("flat ground renders as one flat color")
  void flatGroundIsUniform() {
    for (final int pixel : render(flatGrass())) {
      assertEquals(lit(GRASS_TOP), pixel);
    }
  }

  @Test
  @DisplayName("a missing chunk leaves its pixels transparent rather than black")
  void missingChunkIsTransparent() {
    final int[] pixels = render((chunkX, chunkZ) -> null);
    for (final int pixel : pixels) {
      assertEquals(0, pixel);
    }
  }

  @Test
  @DisplayName(
      "a thin block resting on a column colors the pixel, since that is what is seen from above")
  void capDecidesTheColor() {
    final int[] pixels =
        render(
            world(
                (chunk, x, z) -> {
                  chunk.addRun(GROUND_Y, 0, BlockType.GRASS_BLOCK);
                  chunk.completeColumn(null, x == 4 && z == 4 ? BlockType.TORCH : null);
                }));
    assertEquals(lit(TORCH_TOP), pixels[4 * TILE_SIZE + 4]);
    assertEquals(lit(GRASS_TOP), pixels[5 * TILE_SIZE + 4]);
  }

  @Test
  @DisplayName(
      "a deep flat sea floor reads differently from a shallow one, which its contours cannot say")
  void flatWaterStillShowsItsDepth() {

    final int shallow = render(flatWater(1))[8 * TILE_SIZE + 8];
    final int middling = render(flatWater(6))[8 * TILE_SIZE + 8];
    final int deep = render(flatWater(20))[8 * TILE_SIZE + 8];

    assertNotEquals(shallow, middling, "a puddle and a lake should not draw identically");
    assertNotEquals(middling, deep, "a lake and an ocean should not draw identically");
    assertTrue((deep & 0xFF) < (shallow & 0xFF), "deeper water should be darker");
    assertTrue(((deep >> 16) & 0xFF) < ((shallow >> 16) & 0xFF));
  }

  @Test
  @DisplayName(
      "water takes its color from the surface but its shading from the bed, so depth stays readable")
  void waterIsShadedByItsBed() {

    final int[] pixels =
        render(
            world(
                (chunk, x, z) -> {
                  chunk.addRun(GROUND_Y, GROUND_Y - Math.min(12, Math.max(0, x)), BlockType.WATER);
                  chunk.addRun(GROUND_Y - Math.min(12, Math.max(0, x)) - 1, 0, BlockType.STONE);
                  chunk.completeColumn(null, null);
                }));

    int distinct = 0;
    final boolean @NotNull [] seen = new boolean[1 << 24];
    for (final int pixel : pixels) {
      assertTrue((pixel >>> 24) != 0);
      if (!seen[pixel & 0xFFFFFF]) {
        seen[pixel & 0xFFFFFF] = true;
        distinct++;
      }
    }
    assertTrue(distinct > 1, "a sloping bed under flat water should still produce shading");
  }

  @Test
  @DisplayName("terrain outside the tile still shades its edge pixels")
  void haloOutsideTheTileAffectsEdges() {

    final @NotNull ChunkColumnsSource stepped =
        world(
            (chunk, x, z) -> {
              chunk.addRun(x >= TILE_SIZE ? GROUND_Y - 8 : GROUND_Y, 0, BlockType.GRASS_BLOCK);
              chunk.completeColumn(null, null);
            });
    final int[] pixels = render(stepped);
    assertNotEquals(
        pixels[0],
        pixels[TILE_SIZE - 1],
        "the pixel next to the drop should be shaded differently");
  }

  @Test
  @DisplayName(
      "ground above its neighbor is tinted toward its own side material, giving a terrace its edge")
  void dropTowardPositiveXTintsTowardTheSide() {
    final int[] pixels = render(stepDownAt(8, 3));
    final int onTheEdge = pixels[4 * TILE_SIZE + 8];
    final int wellBack = pixels[4 * TILE_SIZE + 4];

    assertEquals(
        lit(GRASS_TOP), wellBack, "flat ground away from the drop stays the plain top color");
    assertNotEquals(
        lit(GRASS_TOP), onTheEdge, "the block above the drop should show its side material");
    assertTrue(
        brightness(onTheEdge) < brightness(wellBack),
        "an exposed side face is darker than a top face");
  }

  @Test
  @DisplayName(
      "ground below its neighbor is never brightened, which is what stops it reading as a relief map")
  void risingGroundIsNeverBrightened() {
    final int[] pixels = render(stepDownAt(8, 3));

    assertTrue(brightness(pixels[4 * TILE_SIZE + 9]) <= brightness(lit(GRASS_TOP)));
  }

  @Test
  @DisplayName(
      "a column boxed in by taller ground is darkened, giving contact shading at the foot of a step")
  void occludedGroundIsDarkened() {
    final int[] pixels = render(stepDownAt(8, 3));
    final int atTheFoot = pixels[4 * TILE_SIZE + 9];
    final int outInTheOpen = pixels[4 * TILE_SIZE + 14];

    assertEquals(
        lit(GRASS_TOP), outInTheOpen, "open ground well past the step keeps the plain top color");
    assertTrue(
        brightness(atTheFoot) < brightness(outInTheOpen), "the foot of the step should be darker");
  }

  @Test
  @DisplayName("a deeper drop gives more of the pixel over to the side face")
  void deeperDropsShowMoreSide() {
    final int shallow = render(stepDownAt(8, 1))[4 * TILE_SIZE + 8];
    final int deep = render(stepDownAt(8, 6))[4 * TILE_SIZE + 8];
    assertTrue(
        brightness(deep) < brightness(shallow),
        "a six block drop should read darker than a one block drop");
  }

  @Test
  @DisplayName("the two exposed sides are shaded differently, as they are in the isometric view")
  void thePositiveZSideIsDarker() {
    final int alongX = render(stepDownAt(8, 3))[4 * TILE_SIZE + 8];
    final int[] alongZPixels =
        render(
            world(
                (chunk, x, z) -> {
                  chunk.addRun(z > 8 ? GROUND_Y - 3 : GROUND_Y, 0, BlockType.GRASS_BLOCK);
                  chunk.completeColumn(null, null);
                }));
    final int alongZ = alongZPixels[8 * TILE_SIZE + 4];
    assertTrue(
        brightness(alongZ) < brightness(alongX),
        "the face toward positive z is the darker of the two");
  }

  @Test
  @DisplayName("a heavier top face weight keeps flat ground cleaner without removing the terracing")
  void topFaceWeightControlsHowStrongEdgesAre() {
    final @NotNull ChunkColumnsSource source = stepDownAt(8, 2);
    final int strong =
        new TopDownTileRendererImpl(COLORS, 1.0f, 0)
            .render(source, 0, 0, TILE_SIZE)
            .image()
            .getRGB(8, 4);
    final int subtle =
        new TopDownTileRendererImpl(COLORS, 4.0f, 0)
            .render(source, 0, 0, TILE_SIZE)
            .image()
            .getRGB(8, 4);
    assertTrue(
        brightness(strong) < brightness(subtle),
        "a lower weight gives the side faces more of the pixel");
    assertNotEquals(
        lit(GRASS_TOP), subtle, "the terracing should still be visible at a high weight");
  }

  @Test
  @DisplayName("a column with no blocks at all stays transparent")
  void emptyColumnIsTransparent() {
    final int[] pixels =
        render(
            world(
                (chunk, x, z) -> {
                  if (x != 2 || z != 2) {
                    chunk.addRun(GROUND_Y, 0, BlockType.GRASS_BLOCK);
                  }
                  chunk.completeColumn(null, null);
                }));
    assertEquals(0, pixels[2 * TILE_SIZE + 2]);
    assertNotEquals(0, pixels[3 * TILE_SIZE + 3]);
  }

  @Test
  @DisplayName(
      "a top-down tile stores the surface height of every block, and nothing where there is no ground")
  void tileCarriesSurfaceHeights() throws Exception {
    final @NotNull ChunkColumnsSource source =
        world(
            (chunk, x, z) -> {
              if (x == 3 && z == 5) {
                chunk.addRun(GROUND_Y + 7, GROUND_Y + 1, BlockType.STONE);
              }
              if (x != 9 || z != 9) {
                chunk.addRun(GROUND_Y, 0, BlockType.GRASS_BLOCK);
              }
              chunk.completeColumn(null, null);
            });
    final byte @Nullable [] encoded =
        Objects.requireNonNull(
                new TopDownTileRendererImpl(COLORS).render(source, 0, 0, TILE_SIZE).pick())
            .encode();

    assertEquals(1, encoded[15], "one stored value per block");
    final int originHeight =
        ((encoded[11] & 0xFF) << 24)
            | ((encoded[12] & 0xFF) << 16)
            | ((encoded[13] & 0xFF) << 8)
            | (encoded[14] & 0xFF);
    final java.util.zip.@NotNull Inflater inflater = new java.util.zip.Inflater();
    inflater.setInput(encoded, 16, encoded.length - 16);
    final byte @NotNull [] planes = new byte[TILE_SIZE * TILE_SIZE * 4];
    inflater.inflate(planes);
    inflater.end();

    final java.util.function.@NotNull IntUnaryOperator plane =
        offset -> ((planes[offset] & 0xFF) << 8) | (planes[offset + 1] & 0xFF);
    final int pixels = TILE_SIZE * TILE_SIZE;
    assertEquals(
        GROUND_Y + 7,
        originHeight + plane.applyAsInt((pixels + 5 * TILE_SIZE + 3) * 2),
        "the raised column");
    assertEquals(GROUND_Y, originHeight + plane.applyAsInt((pixels) * 2), "plain ground");
    assertEquals(
        0xFFFF, plane.applyAsInt((9 * TILE_SIZE + 9) * 2), "an empty column has no height");
  }

  @FunctionalInterface
  private interface ColumnBuilder {

    void build(ChunkColumnsBuilder chunk, int blockX, int blockZ);
  }
}
