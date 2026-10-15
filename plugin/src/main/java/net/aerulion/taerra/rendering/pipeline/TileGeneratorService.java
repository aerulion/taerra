package net.aerulion.taerra.rendering.pipeline;

import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.region.RegionScanProgress;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.session.ManualRenderSession;
import net.aerulion.taerra.rendering.session.RenderHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface TileGeneratorService permits TileGeneratorServiceImpl {

  @NotNull String FULL_RENDER = "full_render";
  @NotNull String LIMIT_RENDER = "limit_render";
  @NotNull String RADIUS_RENDER = "radius_render";
  @NotNull String AREA_RENDER = "area_render";
  @NotNull String HIDE_RENDER = "hide_render";

  void start();

  void shutdown();

  @Nullable
  ManualRenderSession tryBeginManualRender(@NotNull String mode, @NotNull String worldName);

  void updateManualRenderScanProgress(
      @NotNull ManualRenderSession session, @NotNull RegionScanProgress progress);

  int submitManualRender(
      @NotNull ManualRenderSession session,
      @NotNull RegionScanResult result,
      @NotNull String plannedMessage);

  int cancelManualRender(@NotNull ManualRenderSession session, @NotNull String reason);

  int cancelManualRender(@NotNull String reason);

  @NotNull
  LiveRenderTiming liveTiming();

  @NotNull
  RenderHistory renderHistory();

  @NotNull
  RenderStatusSnapshot statusSnapshot();

  int pyramidDepth(@NotNull TileCoordinate layer);

  int @Nullable [] renderedArea(@NotNull String worldName);

  void setThreads(int configuredThreads);
}
