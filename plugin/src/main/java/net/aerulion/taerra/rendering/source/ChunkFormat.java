package net.aerulion.taerra.rendering.source;

import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

enum ChunkFormat {
  MINECRAFT_26_2(4903) {
    @Override
    @NotNull
    String blockStateName(final @NotNull ListTag palette, final int index) {
      return palette.getCompound(index).flatMap(entry -> entry.getString("Name")).orElse("");
    }
  },
  MINECRAFT_26_3(5023) {
    @Override
    @NotNull
    String blockStateName(final @NotNull ListTag palette, final int index) {
      return palette
          .getString(index)
          .or(() -> palette.getCompound(index).flatMap(entry -> entry.getString("id")))
          .orElse("");
    }
  };

  private final int firstDataVersion;

  ChunkFormat(final int firstDataVersion) {
    this.firstDataVersion = firstDataVersion;
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

  abstract @NotNull String blockStateName(@NotNull ListTag palette, int index);
}
