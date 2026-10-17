package net.aerulion.taerra.rendering.sample;

import java.util.Collection;
import net.aerulion.taerra.rendering.source.ChunkSourceProvider;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public sealed interface ChunkColumnsCache permits ChunkColumnsCacheImpl {

  long knownHeights(@NotNull String worldName, int chunkX, int chunkZ);

  int refreshChanged(@NotNull World world) throws Exception;

  @NotNull
  CachedChunkColumnsSource sourceFor(@NotNull World world, @NotNull ChunkSourceProvider provider);

  void markChanged(@NotNull String worldName, int chunkX, int chunkZ);

  void forget(@NotNull String worldName, @NotNull Collection<Long> chunks, boolean terrain);

  void forgetWorld(@NotNull String worldName);

  long size();

  void resize(int maximumChunks);
}
