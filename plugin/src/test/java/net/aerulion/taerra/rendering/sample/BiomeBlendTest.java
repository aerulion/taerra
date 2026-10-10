package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.aerulion.taerra.rendering.color.BlockColors;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.color.TintColors;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BiomeBlendTest {

  private static final int BLUE = 250;

  private static final @NotNull MapColorResolver COLORS =
      new MapColorResolver() {
        @Override
        public @NotNull BlockColors colorsOf(final @NotNull BlockType blockType) {
          throw new AssertionError();
        }

        @Override
        public @Nullable TintColors tintAt(
            final @Nullable Biome biome, final int blockX, final int blockZ) {
          if (biome == null) {
            return null;
          }
          final int grass = biome == Biome.DESERT ? BLUE : 0;
          return new TintColors(0, grass, 0, 0);
        }
      };

  private static @NotNull ChunkColumns chunk(final int chunkX, final boolean withHole) {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, 0);
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        final int blockX = chunkX * 16 + localX;
        final boolean hole = withHole && localX == 3 && localZ == 3;
        builder.completeColumn(
            hole ? null : blockX < 8 ? Biome.PLAINS : Biome.DESERT, BlockType.AIR);
      }
    }
    return builder.build();
  }

  @Test
  @DisplayName("colours blend over a five block square and stay pure away from a biome border")
  void blendsAcrossBorder() {
    final @NotNull BiomeBlend blend =
        new BiomeBlend((chunkX, chunkZ) -> chunkZ == 0 ? chunk(chunkX, true) : null, COLORS);

    assertEquals(0, blend.at(2, 8).grass());
    assertEquals(BLUE * 2 / 5, blend.at(7, 8).grass());
    assertEquals(BLUE * 3 / 5, blend.at(8, 8).grass());
    assertEquals(BLUE, blend.at(13, 8).grass());
    assertEquals(BLUE, blend.at(30, 8).grass());
    assertNull(blend.at(3, 3), "a column without a biome stays untinted");
    assertEquals(BLUE, blend.at(13, 0).grass(), "missing neighbours are left out of the average");
  }
}
