package net.aerulion.taerra.rendering.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkFormatTest {

  private static @NotNull CompoundTag entry(final @NotNull String key, final @NotNull String name) {
    final @NotNull CompoundTag entry = new CompoundTag();
    entry.putString(key, name);
    return entry;
  }

  @Test
  @DisplayName("chunks pick the format of their DataVersion, from 1.18 up to the server's own")
  void dataVersionsPickTheirFormat() {
    assertNull(ChunkFormat.of(0, 5023));
    assertNull(ChunkFormat.of(2859, 5023));
    assertEquals(ChunkFormat.MINECRAFT_1_18, ChunkFormat.of(2860, 5023));
    assertEquals(ChunkFormat.MINECRAFT_1_20_3, ChunkFormat.of(3692, 5023));
    assertEquals(ChunkFormat.MINECRAFT_1_21_9, ChunkFormat.of(5005, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_3, ChunkFormat.of(5006, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_3, ChunkFormat.of(5100, 5200));
    assertNull(ChunkFormat.of(5024, 5023));
    assertEquals(2860, ChunkFormat.oldestDataVersion());
  }

  @Test
  @DisplayName("palettes before 26.3 name their blocks in Name compounds")
  void nameCompoundPalette() {
    final @NotNull ListTag palette = new ListTag();
    palette.add(entry("Name", "minecraft:deepslate"));
    palette.add(new CompoundTag());

    assertEquals("minecraft:deepslate", ChunkFormat.MINECRAFT_1_21_9.blockStateName(palette, 0));
    assertEquals("", ChunkFormat.MINECRAFT_1_21_9.blockStateName(palette, 1));
  }

  @Test
  @DisplayName("blocks renamed since a chunk was saved read under their current name")
  void renamedBlocks() {
    final @NotNull ListTag palette = new ListTag();
    palette.add(entry("Name", "minecraft:grass"));
    palette.add(entry("Name", "minecraft:chain"));

    assertEquals("minecraft:short_grass", ChunkFormat.MINECRAFT_1_18.blockStateName(palette, 0));
    assertEquals("minecraft:iron_chain", ChunkFormat.MINECRAFT_1_18.blockStateName(palette, 1));
    assertEquals("minecraft:grass", ChunkFormat.MINECRAFT_1_20_3.blockStateName(palette, 0));
    assertEquals("minecraft:iron_chain", ChunkFormat.MINECRAFT_1_20_3.blockStateName(palette, 1));
    assertEquals("minecraft:chain", ChunkFormat.MINECRAFT_1_21_9.blockStateName(palette, 1));
  }

  @Test
  @DisplayName("26.3 palettes name their blocks as strings, or in id compounds with properties")
  void minecraft26_3Palette() {
    final @NotNull ListTag palette = new ListTag();
    palette.add(StringTag.valueOf("minecraft:stone"));
    final @NotNull CompoundTag withProperties = entry("id", "minecraft:tall_seagrass");
    withProperties.put("properties", new CompoundTag());
    palette.add(withProperties);
    palette.add(new CompoundTag());

    assertEquals("minecraft:stone", ChunkFormat.MINECRAFT_26_3.blockStateName(palette, 0));
    assertEquals("minecraft:tall_seagrass", ChunkFormat.MINECRAFT_26_3.blockStateName(palette, 1));
    assertEquals("", ChunkFormat.MINECRAFT_26_3.blockStateName(palette, 2));
  }
}
