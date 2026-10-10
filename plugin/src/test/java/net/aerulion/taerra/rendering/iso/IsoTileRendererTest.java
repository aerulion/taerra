package net.aerulion.taerra.rendering.iso;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.color.BlockColors;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsBuilder;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.Lighting;
import net.aerulion.taerra.rendering.style.MapStyle;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IsoTileRendererTest {

  private static final int MIN_WORLD_Y = -64;
  private static final int MAX_WORLD_Y = 320;
  private static final int GROUND_Y = 64;

  private static final int SUBJECT_X = 216;
  private static final int SUBJECT_Z = 184;

  private static final int STONE_TOP = 0xFF204080;
  private static final int STONE_SIDE = 0xFF3050A0;
  private static final int PLANK_TOP = 0xFF80C040;
  private static final int PLANK_SIDE = 0xFF90D050;
  private static final int TORCH_TOP = 0xFFFFEE00;
  private static final int TORCH_SIDE = 0xFFFFDD00;

  private static final @NotNull MapColorResolver COLORS =
      blockType ->
          new BlockColors() {
            @Override
            public int topColor(final @Nullable Biome biome, final int x, final int z) {
              return blockType == BlockType.OAK_PLANKS
                  ? PLANK_TOP
                  : blockType == BlockType.TORCH ? TORCH_TOP : STONE_TOP;
            }

            @Override
            public int sideColor(final @Nullable Biome biome, final int x, final int z) {
              return blockType == BlockType.OAK_PLANKS
                  ? PLANK_SIDE
                  : blockType == BlockType.TORCH ? TORCH_SIDE : STONE_SIDE;
            }
          };

  private static int[] renderTileAt(
      final @NotNull TestWorld world, final int blockX, final int blockZ, final int surfaceY) {
    return renderTileAt(MapRotation.R0, world, blockX, blockZ, surfaceY);
  }

  private static int[] renderTileAt(
      final @NotNull MapRotation rotation,
      final @NotNull TestWorld world,
      final int blockX,
      final int blockZ,
      final int surfaceY) {
    return renderTileAt(new IsoTileRendererImpl(COLORS), rotation, world, blockX, blockZ, surfaceY);
  }

  private static int[] renderTileAt(
      final @NotNull IsoTileRenderer renderer,
      final @NotNull MapRotation rotation,
      final @NotNull TestWorld world,
      final int blockX,
      final int blockZ,
      final int surfaceY) {
    final @NotNull IsoProjection projection = IsoTileGeometry.projection();
    final int rotatedX = rotation.rotatedX(blockX, blockZ);
    final int rotatedZ = rotation.rotatedZ(blockX, blockZ);
    final int tileX = IsoTileGeometry.screenToTileX(projection.screenX(rotatedX, rotatedZ), 0);
    final int tileY =
        IsoTileGeometry.screenToTileY(projection.screenY(rotatedX, surfaceY, rotatedZ), 0);
    final @NotNull BufferedImage image =
        renderer.renderTile(rotation, tileX, tileY, MIN_WORLD_Y, MAX_WORLD_Y, world, false).image();
    return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
  }

  private static int lit(final int topColor) {
    return Lighting.face(topColor, Lighting.TOP, 0, false, Lighting.NO_CONTACT);
  }

  private static int[] wall(final int sideColor, final int face) {
    return new int[] {
      Lighting.face(sideColor, face, 0, false, Lighting.NO_CONTACT),
      foot(sideColor, face, Lighting.CONTACT_NEAR),
      foot(sideColor, face, Lighting.CONTACT_FOOT)
    };
  }

  private static int foot(final int sideColor, final int face, final int contact) {
    return Lighting.bounce(
        Lighting.face(sideColor, face, 0, false, contact),
        sideColor,
        Lighting.face(STONE_TOP, Lighting.TOP, 0, face == Lighting.POSITIVE_Z, Lighting.NO_CONTACT),
        contact);
  }

  private static boolean containsWall(
      final int @NotNull [] pixels, final int sideColor, final int face) {
    return countWall(pixels, sideColor, face) > 0;
  }

  private static int countWall(final int @NotNull [] pixels, final int sideColor, final int face) {
    int total = 0;
    for (final int color : wall(sideColor, face)) {
      total += count(pixels, color);
    }
    return total;
  }

  private static boolean contains(final int @NotNull [] pixels, final int color) {
    for (final int pixel : pixels) {
      if (pixel == color) {
        return true;
      }
    }
    return false;
  }

  private static int count(final int @NotNull [] pixels, final int color) {
    int found = 0;
    for (final int pixel : pixels) {
      if (pixel == color) {
        found++;
      }
    }
    return found;
  }

  private static @NotNull TestWorld world(final @NotNull Blocks blocks) {
    return new TestWorld(true) {
      @Override
      public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
        final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
        for (int localX = 0; localX < 16; localX++) {
          for (int localZ = 0; localZ < 16; localZ++) {
            blocks.column(
                builder, chunkX * 16 + localX - SUBJECT_X, chunkZ * 16 + localZ - SUBJECT_Z);
            builder.completeColumn(null, null);
          }
        }
        return builder.build();
      }
    };
  }

  private static int[] renderWithShadows(final @NotNull TestWorld world, final int surfaceY) {
    return renderTileAt(
        new IsoTileRendererImpl(COLORS, 16, MapStyle.TOP_FACE_WEIGHT),
        MapRotation.R0,
        world,
        SUBJECT_X,
        SUBJECT_Z,
        surfaceY);
  }

  private static boolean containsTop(
      final int @NotNull [] pixels, final int color, final boolean shadowed) {
    for (int taller = 0; taller <= 8; taller++) {
      if (contains(
          pixels, Lighting.face(color, Lighting.TOP, taller, shadowed, Lighting.NO_CONTACT))) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("flat ground fills the tile completely, so the diamonds really do leave no holes")
  void flatGroundLeavesNoHoles() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final int[] pixels =
          renderTileAt(rotation, new TestWorld(true), SUBJECT_X, SUBJECT_Z, GROUND_Y + 1);
      for (int index = 0; index < pixels.length; index++) {
        assertTrue(
            (pixels[index] >>> 24) != 0,
            "transparent pixel at "
                + (index % 256)
                + ","
                + (index / 256)
                + " over solid ground at "
                + rotation);
      }
    }
  }

  @Test
  @DisplayName("flat ground shows only top faces, because every side is hidden by its neighbor")
  void flatGroundHidesEverySideFace() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final int[] pixels =
          renderTileAt(rotation, new TestWorld(true), SUBJECT_X, SUBJECT_Z, GROUND_Y + 1);
      for (final int pixel : pixels) {
        assertEquals(
            lit(STONE_TOP), pixel, "flat ground should be uniform top-face color at " + rotation);
      }
    }
  }

  @Test
  @DisplayName("a raised block shows both of its side faces, each in its own material and shade")
  void raisedBlockShowsItsOwnWalls() {

    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull TestWorld world =
          new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 2, GROUND_Y + 1);
      final int[] pixels = renderTileAt(rotation, world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 3);

      assertTrue(
          contains(pixels, lit(PLANK_TOP)),
          "the raised block's top face should be drawn at " + rotation);
      assertTrue(
          containsWall(pixels, PLANK_SIDE, Lighting.POSITIVE_X),
          "the nearer wall should be the wall material, not the roof's, at " + rotation);
      assertTrue(
          containsWall(pixels, PLANK_SIDE, Lighting.POSITIVE_Z),
          "the further wall should be the wall material, not the roof's, at " + rotation);
      assertTrue(
          contains(pixels, lit(STONE_TOP)),
          "surrounding ground should still be drawn at " + rotation);
    }
  }

  @Test
  @DisplayName(
      "a rotation shows the same block from another corner, so its walls land somewhere else")
  void rotationMovesTheWallsWithoutLosingThem() {
    final int @NotNull [][] byRotation = new int[MapRotation.values().length][];
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull TestWorld world =
          new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 4, GROUND_Y + 1);
      byRotation[rotation.index()] =
          renderTileAt(rotation, world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 5);
    }

    final int reference = countWall(byRotation[0], PLANK_SIDE, Lighting.POSITIVE_X);
    assertTrue(reference > 0);
    for (final @NotNull MapRotation rotation : MapRotation.values()) {

      assertEquals(
          reference,
          countWall(byRotation[rotation.index()], PLANK_SIDE, Lighting.POSITIVE_X),
          "the visible wall changed size at " + rotation);
    }
    assertFalse(
        java.util.Arrays.equals(byRotation[0], byRotation[MapRotation.R90.index()]),
        "a quarter turn must produce a different picture");
  }

  @Test
  @DisplayName("the two visible side faces are shaded differently, which is what reads as depth")
  void sideFacesAreShadedApart() {
    final @NotNull TestWorld world =
        new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 2, GROUND_Y + 1);
    final int[] pixels = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 3);

    for (final int contact :
        new int[] {Lighting.NO_CONTACT, Lighting.CONTACT_NEAR, Lighting.CONTACT_FOOT}) {
      assertNotEquals(
          Lighting.face(PLANK_SIDE, Lighting.POSITIVE_X, 0, false, contact),
          Lighting.face(PLANK_SIDE, Lighting.POSITIVE_Z, 0, false, contact),
          "the two side faces must not share a shade");
    }
    assertTrue(
        countWall(pixels, PLANK_SIDE, Lighting.POSITIVE_X) > 0
            && countWall(pixels, PLANK_SIDE, Lighting.POSITIVE_Z) > 0);
  }

  @Test
  @DisplayName("a cap colors the top face without extruding as a cube")
  void capColorsTheTopFaceOnly() {
    final @NotNull TestWorld world = new TestWorld(true).cap(SUBJECT_X, SUBJECT_Z, BlockType.TORCH);
    final int[] pixels = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 1);

    assertTrue(
        contains(pixels, lit(TORCH_TOP)), "the torch should color the top face of its column");
    assertFalse(
        containsWall(pixels, TORCH_SIDE, Lighting.POSITIVE_X),
        "a torch must not produce side faces of its own");
    assertFalse(
        containsWall(pixels, TORCH_SIDE, Lighting.POSITIVE_Z),
        "a torch must not produce side faces of its own");
  }

  @Test
  @DisplayName("an empty world renders a fully transparent tile rather than a black one")
  void emptyWorldRendersTransparent() {
    final @NotNull BufferedImage image =
        new IsoTileRendererImpl(COLORS)
            .renderTile(
                MapRotation.R0, 0, 0, MIN_WORLD_Y, MAX_WORLD_Y, (chunkX, chunkZ) -> null, false)
            .image();
    final int[] pixels =
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    for (final int pixel : pixels) {
      assertEquals(0, pixel);
    }
  }

  @Test
  @DisplayName("the map ends at an ungenerated neighbor with a wall, not a view into its caves")
  void ungeneratedNeighborShowsTheEdgeWall() {

    final @NotNull TestWorld world = new TestWorld(false).groundBottom(MIN_WORLD_Y);
    final int[] pixels = renderTileAt(world, 8, 8, GROUND_Y + 1);

    assertTrue(contains(pixels, lit(STONE_TOP)), "the lone chunk should still draw its top faces");
    assertTrue(
        containsWall(pixels, STONE_SIDE, Lighting.POSITIVE_X),
        "the edge of the map should show its side, as it does over the void");
  }

  @Test
  @DisplayName(
      "a raised block's wall is bounded by its own run, however deep the ground beneath goes")
  void wallIsBoundedByItsRun() {
    final @NotNull TestWorld shallow =
        new TestWorld(true)
            .groundBottom(GROUND_Y - 2)
            .raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 1, GROUND_Y + 1);
    final @NotNull TestWorld deep =
        new TestWorld(true)
            .groundBottom(MIN_WORLD_Y)
            .raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 1, GROUND_Y + 1);

    final int shallowPixels =
        countWall(
            renderTileAt(shallow, SUBJECT_X, SUBJECT_Z, GROUND_Y + 2),
            PLANK_SIDE,
            Lighting.POSITIVE_X);
    assertTrue(shallowPixels > 0);
    assertEquals(
        shallowPixels,
        countWall(
            renderTileAt(deep, SUBJECT_X, SUBJECT_Z, GROUND_Y + 2),
            PLANK_SIDE,
            Lighting.POSITIVE_X));
  }

  @Test
  @DisplayName(
      "a courtyard deep inside a block of towers is drawn down to its floor, across chunk borders")
  void courtyardInsideTallBlockIsDrawnToItsFloor() {
    final int courtyardX = SUBJECT_X - 3;
    final int courtyardZ = SUBJECT_Z - 3;
    final int roofY = 250;
    final int chunkX = Math.floorDiv(SUBJECT_X, 16);
    final int chunkZ = Math.floorDiv(SUBJECT_Z, 16);
    final @NotNull TestWorld world =
        new TestWorld(true) {
          @Override
          public @Nullable ChunkColumns columnsFor(final int x, final int z) {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(x, z);
            final boolean towers = Math.abs(x - chunkX) <= 1 && Math.abs(z - chunkZ) <= 1;
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                final int blockX = x * 16 + localX;
                final int blockZ = z * 16 + localZ;
                final boolean courtyard =
                    blockX >= courtyardX
                        && blockX < courtyardX + 6
                        && blockZ >= courtyardZ
                        && blockZ < courtyardZ + 6;
                if (towers && !courtyard) {
                  builder.addRun(roofY, GROUND_Y + 1, BlockType.OAK_PLANKS);
                }
                builder.addRun(GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
                builder.completeColumn(null, null);
              }
            }
            return builder.build();
          }
        };

    final int[] pixels = renderTileAt(world, courtyardX + 5, courtyardZ + 5, GROUND_Y + 1);
    assertTrue(
        contains(pixels, lit(STONE_TOP)), "the courtyard floor should be visible from above");
    assertTrue(
        containsWall(pixels, PLANK_SIDE, Lighting.POSITIVE_X),
        "the courtyard's far walls should reach its floor");
  }

  @Test
  @DisplayName("a step in the bed under shallow water shows its side, tinted by the water over it")
  void shoreStepShowsThroughShallowWater() {
    final @NotNull TestWorld world =
        new TestWorld(true) {
          @Override
          public @Nullable ChunkColumns columnsFor(final int x, final int z) {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(x, z);
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                if (x * 16 + localX > SUBJECT_X) {
                  builder.addRun(GROUND_Y, GROUND_Y, BlockType.WATER);
                  builder.addRun(GROUND_Y - 1, MIN_WORLD_Y, BlockType.STONE);
                } else {
                  builder.addRun(GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
                }
                builder.completeColumn(null, null);
              }
            }
            return builder.build();
          }
        };

    final int[] pixels = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 1);
    final int wetWall =
        MapStyle.underwater(
            foot(STONE_SIDE, Lighting.POSITIVE_X, Lighting.CONTACT_FOOT),
            Lighting.face(STONE_TOP, Lighting.WATER, 0, false, Lighting.NO_CONTACT),
            1);
    assertTrue(
        contains(pixels, wetWall), "the shore's underwater side should be drawn through the water");
    assertFalse(
        containsWall(pixels, STONE_SIDE, Lighting.POSITIVE_X), "and never as if it were dry");
  }

  @Test
  @DisplayName(
      "a run capped by another draws no top face, so walls are not notched where their material changes")
  void cappedRunDrawsNoTopFace() {

    final @NotNull TestWorld world =
        new TestWorld(true) {
          @Override
          public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                final int blockX = chunkX * 16 + localX;
                final int blockZ = chunkZ * 16 + localZ;
                if (blockX == SUBJECT_X && blockZ == SUBJECT_Z) {
                  builder.addRun(GROUND_Y + 4, GROUND_Y + 4, BlockType.TORCH);
                  builder.addRun(GROUND_Y + 3, GROUND_Y + 1, BlockType.OAK_PLANKS);
                }
                builder.addRun(GROUND_Y, 0, BlockType.STONE);
                builder.completeColumn(null, null);
              }
            }
            return builder.build();
          }
        };

    final int[] pixels = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 5);
    assertTrue(
        contains(pixels, lit(TORCH_TOP)), "the capping block's own top face must still be drawn");
    assertFalse(
        contains(pixels, lit(PLANK_TOP)),
        "the covered run's top face must not be drawn through the block resting on it");
    assertTrue(
        containsWall(pixels, PLANK_SIDE, Lighting.POSITIVE_X),
        "the wall itself should still be visible");
  }

  @Test
  @DisplayName(
      "a run enclosed by its neighbors is darkened even when the roof above it is in the open")
  void occlusionFollowsEachRunRatherThanTheColumnTop() {

    final @NotNull TestWorld world =
        new TestWorld(true) {
          @Override
          public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                final int blockX = chunkX * 16 + localX;
                final int blockZ = chunkZ * 16 + localZ;
                if (blockX == SUBJECT_X && blockZ == SUBJECT_Z) {
                  builder.addRun(GROUND_Y + 4, GROUND_Y + 4, BlockType.OAK_PLANKS);
                  builder.addRun(GROUND_Y - 1, GROUND_Y - 4, BlockType.OAK_PLANKS);
                  builder.completeColumn(null, null);
                  continue;
                }

                final boolean nearNeighbor =
                    blockX >= SUBJECT_X
                        && blockZ >= SUBJECT_Z
                        && blockX <= SUBJECT_X + 1
                        && blockZ <= SUBJECT_Z + 1;
                builder.addRun(
                    nearNeighbor ? GROUND_Y - 20 : GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
                builder.completeColumn(null, null);
              }
            }
            return builder.build();
          }
        };

    final int[] pixels = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 5);

    assertTrue(contains(pixels, lit(PLANK_TOP)), "the roof should be drawn unoccluded");

    assertTrue(
        contains(pixels, Lighting.face(PLANK_TOP, Lighting.TOP, 5, false, Lighting.NO_CONTACT)),
        "the floor under the roof should be darkened by the ground standing over it");
  }

  @Test
  @DisplayName("a floor under a roof reaching out toward the sun is in its shadow")
  void floorUnderWideRoofIsShadowed() {
    final @NotNull TestWorld roofed =
        world(
            (builder, dx, dz) -> {
              if (dx >= -1 && dx <= 12 && dz >= -12 && dz <= 0) {
                builder.addRun(GROUND_Y + 3, GROUND_Y + 3, BlockType.TORCH);
              }
              builder.addRun(
                  GROUND_Y,
                  MIN_WORLD_Y,
                  dx == 0 && dz == 0 ? BlockType.OAK_PLANKS : BlockType.STONE);
            });

    final int[] pixels = renderWithShadows(roofed, GROUND_Y + 1);
    assertTrue(
        containsTop(pixels, PLANK_TOP, true), "the floor under the roof should be in its shadow");
    assertFalse(containsTop(pixels, PLANK_TOP, false), "and never lit");
  }

  @Test
  @DisplayName(
      "a floor under a single block of overhang still catches the sun slanting in from the side")
  void floorUnderSmallOverhangIsLit() {
    final @NotNull TestWorld world =
        world(
            (builder, dx, dz) -> {
              if (dx == 0 && dz == 0) {
                builder.addRun(GROUND_Y + 3, GROUND_Y + 3, BlockType.TORCH);
              }
              builder.addRun(
                  GROUND_Y,
                  MIN_WORLD_Y,
                  dx == 0 && dz == 0 ? BlockType.OAK_PLANKS : BlockType.STONE);
            });

    final int[] pixels = renderWithShadows(world, GROUND_Y + 1);
    assertTrue(
        containsTop(pixels, PLANK_TOP, false),
        "one block overhead cannot shade a floor the sun reaches");
    assertFalse(containsTop(pixels, PLANK_TOP, true));
  }

  @Test
  @DisplayName("a wall is shadowed block by block, only as far up as the caster reaches")
  void wallIsShadowedPerBlock() {
    final @NotNull TestWorld world =
        world(
            (builder, dx, dz) -> {
              if (dx == 0 && dz == 0) {
                builder.addRun(GROUND_Y + 16, GROUND_Y + 1, BlockType.OAK_PLANKS);
              } else if ((dz == -4 || dz == -5) && dx >= -5 && dx <= 15) {
                builder.addRun(GROUND_Y + 6, GROUND_Y + 1, BlockType.STONE);
              }
              builder.addRun(GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
            });

    final int[] pixels = renderWithShadows(world, GROUND_Y + 8);
    assertTrue(
        contains(
            pixels, Lighting.face(PLANK_SIDE, Lighting.POSITIVE_X, 0, false, Lighting.NO_CONTACT)),
        "the top of the pillar's sunlit face should be in the sun");
    assertTrue(
        contains(
            pixels, Lighting.face(PLANK_SIDE, Lighting.POSITIVE_X, 0, true, Lighting.NO_CONTACT)),
        "its foot should be in the wall's shadow");
  }

  @Test
  @DisplayName(
      "a wall in an inner corner loses the sky the other wall takes, an open wall does not")
  void innerCornerDarkensTheWall() {
    final @NotNull TestWorld corner =
        world(
            (builder, dx, dz) -> {
              if ((dx == 0 && dz <= 0) || (dz == -1 && dx > 0 && dx <= 10)) {
                builder.addRun(GROUND_Y + 20, GROUND_Y + 1, BlockType.OAK_PLANKS);
              }
              builder.addRun(GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
            });
    final @NotNull TestWorld open =
        world(
            (builder, dx, dz) -> {
              if (dx == 0 && dz <= 0) {
                builder.addRun(GROUND_Y + 20, GROUND_Y + 1, BlockType.OAK_PLANKS);
              }
              builder.addRun(GROUND_Y, MIN_WORLD_Y, BlockType.STONE);
            });

    final int cornered =
        Lighting.face(PLANK_SIDE, Lighting.POSITIVE_X, 3, false, Lighting.NO_CONTACT);
    assertTrue(
        contains(renderTileAt(corner, SUBJECT_X, SUBJECT_Z, GROUND_Y + 10), cornered),
        "the corner wall sees the other wall along it and darkens");
    assertFalse(
        contains(renderTileAt(open, SUBJECT_X, SUBJECT_Z, GROUND_Y + 10), cornered),
        "without the second wall nothing takes its sky");
  }

  @Test
  @DisplayName("a tower taller than 48 blocks keeps its wall all the way down to the ground")
  void tallWallReachesTheGroundRatherThanStoppingBelowItsRoof() {

    final int towerTop = GROUND_Y + 100;
    final @NotNull TestWorld world =
        new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, towerTop, GROUND_Y + 1);

    final int[] atTheFoot = renderTileAt(world, SUBJECT_X, SUBJECT_Z, GROUND_Y + 1);
    assertTrue(
        containsWall(atTheFoot, PLANK_SIDE, Lighting.POSITIVE_X),
        "the tower's wall should still be drawn where it meets the ground");
    assertTrue(
        containsWall(atTheFoot, PLANK_SIDE, Lighting.POSITIVE_Z),
        "both of the tower's visible walls should reach the ground");
    assertTrue(
        contains(atTheFoot, lit(STONE_TOP)), "the ground around the tower should be drawn too");
  }

  @Test
  @DisplayName("a tower whose footprint fills a chunk still reaches the street in the next one")
  void tallWallReachesGroundOutsideItsOwnChunk() {

    final int towerTop = GROUND_Y + 100;
    final int towerChunkX = Math.floorDiv(SUBJECT_X, 16);
    final int towerChunkZ = Math.floorDiv(SUBJECT_Z, 16);
    final @NotNull TestWorld world =
        new TestWorld(true) {
          @Override
          public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
            final boolean tower = chunkX == towerChunkX && chunkZ == towerChunkZ;
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                if (tower) {
                  builder.addRun(towerTop, GROUND_Y + 1, BlockType.OAK_PLANKS);
                }
                builder.addRun(GROUND_Y, 0, BlockType.STONE);
                builder.completeColumn(null, null);
              }
            }
            return builder.build();
          }
        };

    final int[] atTheFoot =
        renderTileAt(world, towerChunkX * 16 + 15, towerChunkZ * 16 + 15, GROUND_Y + 1);
    assertTrue(
        containsWall(atTheFoot, PLANK_SIDE, Lighting.POSITIVE_X),
        "the tower's wall should reach the ground in the neighboring chunk");
    assertTrue(contains(atTheFoot, lit(STONE_TOP)), "that neighboring ground should be drawn too");
  }

  @Test
  @DisplayName("rendering the same tile twice produces identical pixels")
  void renderingIsDeterministic() {
    final int[] first =
        renderTileAt(
            new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 3, GROUND_Y + 1),
            SUBJECT_X,
            SUBJECT_Z,
            GROUND_Y + 4);
    final int[] second =
        renderTileAt(
            new TestWorld(true).raise(SUBJECT_X, SUBJECT_Z, GROUND_Y + 3, GROUND_Y + 1),
            SUBJECT_X,
            SUBJECT_Z,
            GROUND_Y + 4);
    assertArrayEquals(first, second);
  }

  @FunctionalInterface
  private interface Blocks {

    void column(ChunkColumnsBuilder builder, int dx, int dz);
  }

  private static class TestWorld implements ChunkColumnsSource {

    private final @NotNull Map<Long, ChunkColumns> cache = new HashMap<>();
    private final @NotNull Map<Long, int[]> raisedColumns = new HashMap<>();
    private final @NotNull Map<Long, BlockType> caps = new HashMap<>();
    private final boolean infinite;
    private int groundBottomY;

    private TestWorld(final boolean infinite) {
      this.infinite = infinite;
    }

    private static long key(final int a, final int b) {
      return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    @NotNull
    TestWorld raise(final int blockX, final int blockZ, final int topY, final int bottomY) {
      this.raisedColumns.put(key(blockX, blockZ), new int[] {topY, bottomY});
      return this;
    }

    @NotNull
    TestWorld cap(final int blockX, final int blockZ, final BlockType blockType) {
      this.caps.put(key(blockX, blockZ), blockType);
      return this;
    }

    @NotNull
    TestWorld groundBottom(final int bottomY) {
      this.groundBottomY = bottomY;
      return this;
    }

    @Override
    public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
      if (!this.infinite && (chunkX != 0 || chunkZ != 0)) {
        return null;
      }
      return this.cache.computeIfAbsent(
          key(chunkX, chunkZ),
          ignored -> {
            final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
            for (int localX = 0; localX < 16; localX++) {
              for (int localZ = 0; localZ < 16; localZ++) {
                final int blockX = chunkX * 16 + localX;
                final int blockZ = chunkZ * 16 + localZ;
                final int[] raised = this.raisedColumns.get(key(blockX, blockZ));
                if (raised != null) {
                  builder.addRun(raised[0], raised[1], BlockType.OAK_PLANKS);
                }
                builder.addRun(GROUND_Y, this.groundBottomY, BlockType.STONE);
                builder.completeColumn(null, this.caps.get(key(blockX, blockZ)));
              }
            }
            return builder.build();
          });
    }
  }
}
