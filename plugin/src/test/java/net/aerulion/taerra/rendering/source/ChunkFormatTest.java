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
  @DisplayName("chunks pick the format of their DataVersion, from 26.2 up to the server's own")
  void dataVersionsPickTheirFormat() {
    assertNull(ChunkFormat.of(0, 5023));
    assertNull(ChunkFormat.of(4902, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_2, ChunkFormat.of(4903, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_2, ChunkFormat.of(5022, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_3, ChunkFormat.of(5023, 5023));
    assertEquals(ChunkFormat.MINECRAFT_26_3, ChunkFormat.of(5100, 5200));
    assertNull(ChunkFormat.of(5024, 5023));
    assertEquals(4903, ChunkFormat.oldestDataVersion());
  }

  @Test
  @DisplayName("26.2 palettes name their blocks in Name compounds")
  void minecraft26_2Palette() {
    final @NotNull ListTag palette = new ListTag();
    palette.add(entry("Name", "minecraft:deepslate"));
    palette.add(new CompoundTag());

    assertEquals("minecraft:deepslate", ChunkFormat.MINECRAFT_26_2.blockStateName(palette, 0));
    assertEquals("", ChunkFormat.MINECRAFT_26_2.blockStateName(palette, 1));
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
