package net.aerulion.taerra.rendering.region;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface RegionScanListener {

  void onProgress(@NotNull RegionScanProgress progress);
}
