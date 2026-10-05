package net.aerulion.taerra.rendering.pipeline;

import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.region.RegionScanProgress;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.session.RenderHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface TileGeneratorService permits TileGeneratorServiceImpl {

  @NotNull String FULL_RENDER = "full_render";
  @NotNull String LIMIT_RENDER = "limit_render";

  void start();

  void shutdown();

  boolean tryBeginManualRender(@NotNull String mode, @NotNull String worldName);

  void updateManualRenderScanProgress(
      @NotNull String worldName, @NotNull RegionScanProgress progress);

  int submitManualRender(
      @NotNull String mode,
      @NotNull String worldName,
      @NotNull RegionScanResult result,
      @NotNull String plannedMessage);

  int cancelManualRender(@NotNull String reason);

  @NotNull
  LiveRenderTiming liveTiming();

  @NotNull
  RenderHistory renderHistory();

  @NotNull
  RenderStatusSnapshot statusSnapshot();

  int pyramidDepth(@NotNull TileCoordinate layer);

  int @Nullable [] renderedArea(@NotNull String worldName);
}
