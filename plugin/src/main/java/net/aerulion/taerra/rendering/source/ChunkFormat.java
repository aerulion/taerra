package net.aerulion.taerra.rendering.source;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

enum ChunkFormat {
  MINECRAFT_1_18(2860, Palette.NAME_COMPOUNDS, Map.of()),
  MINECRAFT_1_20_3(
      3692, Palette.NAME_COMPOUNDS, Map.of("minecraft:grass", "minecraft:short_grass")),
  MINECRAFT_1_21_9(4541, Palette.NAME_COMPOUNDS, Map.of("minecraft:chain", "minecraft:iron_chain")),
  MINECRAFT_26_3(5006, Palette.IDS, Map.of());

  static {
    final ChunkFormat @NotNull [] formats = values();
    for (int i = formats.length - 2; i >= 0; i--) {
      final @NotNull ChunkFormat next = formats[i + 1];
      final @NotNull Map<String, String> renames = new HashMap<>(next.renamesToCurrent);
      next.blockRenames.forEach(
          (name, renamed) ->
              renames.put(name, next.renamesToCurrent.getOrDefault(renamed, renamed)));
      formats[i].renamesToCurrent = Map.copyOf(renames);
    }
  }

  private final int firstDataVersion;
  private final @NotNull Palette palette;
  private final @NotNull Map<String, String> blockRenames;
  private @NotNull Map<String, String> renamesToCurrent = Map.of();

  ChunkFormat(
      final int firstDataVersion,
      final @NotNull Palette palette,
      final @NotNull Map<String, String> blockRenames) {
    this.firstDataVersion = firstDataVersion;
    this.palette = palette;
    this.blockRenames = blockRenames;
  }

  static int oldestDataVersion() {
    return values()[0].firstDataVersion;
  }

  static @Nullable ChunkFormat of(final int dataVersion, final int currentDataVersion) {
    if (dataVersion > currentDataVersion) {
      return null;
    }
    @Nullable ChunkFormat match = null;
    for (final @NotNull ChunkFormat format : values()) {
      if (dataVersion >= format.firstDataVersion) {
        match = format;
      }
    }
    return match;
  }

  @NotNull
  String blockStateName(final @NotNull ListTag palette, final int index) {
    final @NotNull String name = this.palette.blockStateName(palette, index);
    return this.renamesToCurrent.getOrDefault(name, name);
  }

  private enum Palette {
    NAME_COMPOUNDS {
      @Override
      @NotNull
      String blockStateName(final @NotNull ListTag palette, final int index) {
        return palette.getCompound(index).flatMap(entry -> entry.getString("Name")).orElse("");
      }
    },
    IDS {
      @Override
      @NotNull
      String blockStateName(final @NotNull ListTag palette, final int index) {
        return palette
            .getString(index)
            .or(() -> palette.getCompound(index).flatMap(entry -> entry.getString("id")))
            .orElse("");
      }
    };

    abstract @NotNull String blockStateName(@NotNull ListTag palette, int index);
  }
}
