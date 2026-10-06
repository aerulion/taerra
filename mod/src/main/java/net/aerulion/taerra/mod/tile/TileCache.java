package net.aerulion.taerra.mod.tile;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

public sealed interface TileCache permits TileCacheImpl {

  @Nullable
  GpuTextureView peek(TileKey key);

  @Nullable
  GpuTextureView get(TileKey key);

  boolean wanted(TileKey key);

  boolean request(TileKey key);

  void refresh(TileKey key);

  void refreshAll();

  void refreshWorld(String world);

  void close();
}
