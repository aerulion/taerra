package net.aerulion.taerra.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.chat.Formats;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.iso.IsoRenderStats;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.pipeline.LiveRenderTiming;
import net.aerulion.taerra.rendering.pipeline.RenderStatusSnapshot;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.session.CompletedRender;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.rendering.session.ManualRenderTimingSnapshot;
import net.aerulion.taerra.storage.TileCache;
import net.aerulion.taerra.storage.TileCacheStats;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public final class StatusSubCommand implements TaerraSubCommand {

  private final @NotNull TileGeneratorService generator;
  private final @NotNull TileCache tileCache;
  private final @NotNull IsoTileService isoTileService;
  private final @NotNull ChunkColumnsCache chunkColumnsCache;

  public StatusSubCommand(
      final @NotNull TileGeneratorService generator,
      final @NotNull TileCache tileCache,
      final @NotNull IsoTileService isoTileService,
      final @NotNull ChunkColumnsCache chunkColumnsCache) {
    super();
    this.generator = generator;
    this.tileCache = tileCache;
    this.isoTileService = isoTileService;
    this.chunkColumnsCache = chunkColumnsCache;
  }

  private static @NotNull Component mode(final @NotNull String mode) {
    return Component.translatable("taerra.render.mode." + mode, mode);
  }

  private static @NotNull Component phase(final @NotNull String phase) {
    return Component.translatable(
        "taerra.render.phase." + phase,
        phase,
        switch (phase) {
          case "completed" -> Messages.GREEN;
          case "canceled" -> Messages.RED;
          default -> Messages.GOLD;
        });
  }

  private static @NotNull Component millis(final @NotNull String key, final double millis) {
    return Messages.tr(key, Messages.value(Formats.millis(millis)));
  }

  private static @NotNull Component summary(final @NotNull CompletedRender render) {
    return Messages.parts(
        mode(render.mode()),
        Messages.world(render.worldName()),
        Messages.value(Formats.duration(render.durationMillis())),
        phase(render.outcome()));
  }

  private static @NotNull Component progress(final double fraction) {
    return Component.text()
        .append(Messages.bar(fraction))
        .append(Component.space())
        .append(Messages.value(Formats.percent(fraction)))
        .build();
  }

  private static @NotNull Component renderer(final @NotNull RenderStatusSnapshot snapshot) {
    if (!snapshot.running()) {
      return Messages.row(
          "status.renderer",
          Component.text("● ", Messages.RED).append(Messages.tr("status.stopped")));
    }
    if (snapshot.totalQueuedTiles() == 0 && snapshot.totalInFlightTiles() == 0) {
      return Messages.row(
          "status.renderer",
          Component.text("● ", Messages.GREEN).append(Messages.tr("status.idle")));
    }
    return Messages.row(
            "status.renderer",
            Component.text("● ", Messages.GOLD).append(Messages.tr("status.working")),
            Messages.tr("status.queued", Messages.value(snapshot.totalQueuedTiles())),
            Messages.value(Formats.rate(snapshot.totalTilesPerSecond())))
        .hoverEvent(
            Messages.lines(
                    List.of(
                        Messages.tr(
                            "status.workers",
                            Messages.value(
                                snapshot.busyBaseWorkers() + "/" + snapshot.baseThreads()),
                            Messages.value(
                                snapshot.busyZoomWorkers() + "/" + snapshot.zoomThreads())),
                        Messages.tr(
                            "status.in_flight", Messages.value(snapshot.totalInFlightTiles()))))
                .color(Messages.TEXT));
  }

  private static void addManualRender(
      final @NotNull List<Component> lines, final @NotNull ManualRenderSessionSnapshot session) {
    lines.add(
        Messages.row(
            "status.render",
            mode(session.mode()),
            Messages.world(session.worldName()),
            phase(session.phase())));

    if ("scanning".equals(session.phase())) {
      lines.add(
          Messages.indent(
              progress(session.regionPercent() / 100.0),
              Messages.tr(
                  "status.regions_scanned",
                  Messages.value(session.scannedRegions()),
                  Messages.value(session.totalRegions()))));
      return;
    }

    lines.add(
        Messages.indent(
                progress(session.percent() / 100.0),
                session.hasEstimate()
                    ? Messages.tr(
                        "status.remaining",
                        Messages.value(Formats.duration(session.remainingMillis())))
                    : Messages.tr(
                        "status.elapsed",
                        Messages.value(Formats.duration(session.elapsedMillis()))))
            .hoverEvent(
                Messages.lines(
                        List.of(
                            Messages.tr(
                                "status.tiles_drawn",
                                Messages.value(session.completedTiles()),
                                Messages.value(
                                    (session.totalEstimated() ? "~" : "") + session.totalTiles())),
                            Messages.tr(
                                "status.elapsed",
                                Messages.value(Formats.duration(session.elapsedMillis()))),
                            Messages.tr(
                                "status.in_flight", Messages.value(session.inFlightTiles())),
                            Messages.tr("status.queued", Messages.value(session.queuedTiles()))))
                    .color(Messages.TEXT)));
  }

  private static @NotNull Component updates(final @NotNull LiveRenderTiming live) {
    return Messages.row(
            "status.updates",
            Messages.tr("status.batches", Messages.value(live.batches())),
            millis("status.each", live.averageBatchMillis()))
        .hoverEvent(
            Messages.lines(
                    List.of(
                        Messages.section("status.updates_title"),
                        Messages.row(
                            "status.volume",
                            Messages.tr("unit.tiles", Messages.value(live.tiles())),
                            Messages.tr(
                                "status.chunks_each",
                                Messages.value(Formats.count(live.averageChunksPerBatch())))),
                        Messages.row(
                            "status.per_batch",
                            millis("status.read", live.averageChunkLoadMillis()),
                            millis("status.refresh", live.averageSnapshotMillis()),
                            millis("status.sample", live.averageSampleMillis()),
                            millis("status.draw", live.averageDrawMillis()),
                            millis("status.persist", live.averagePersistMillis()))))
                .color(Messages.TEXT));
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "status";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_STATUS;
  }

  @Override
  public boolean register() {
    return true;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .executes(
            context -> {
              final @NotNull RenderStatusSnapshot snapshot = this.generator.statusSnapshot();
              final @NotNull List<Component> lines = new ArrayList<>();
              lines.add(Messages.header(Messages.tr("status.title")));
              lines.add(renderer(snapshot));
              if (snapshot.manualRenderSession().active()) {
                addManualRender(lines, snapshot.manualRenderSession());
              }
              lines.add(tiles(snapshot));
              final @NotNull LiveRenderTiming live = this.generator.liveTiming();
              if (!live.isEmpty()) {
                lines.add(updates(live));
              }
              if (!snapshot.manualRenderSession().active()) {
                lines.add(lastRender());
              }
              feedback(context, Messages.lines(lines));
              return SUCCESS;
            });
  }

  private @NotNull Component tiles(final @NotNull RenderStatusSnapshot snapshot) {
    final @NotNull TileCacheStats cache = this.tileCache.stats();
    final @NotNull ManualRenderTimingSnapshot timing = snapshot.manualTiming();
    final @NotNull IsoRenderStats iso = this.isoTileService.stats();

    final @NotNull Component topDown =
        Messages.tr(
                "status.top_down",
                Messages.value(snapshot.completedZoom0Tiles() + snapshot.completedZoomTiles()))
            .hoverEvent(
                Messages.lines(
                        List.of(
                            Messages.section("status.top_down_title"),
                            Messages.row(
                                "status.rendered",
                                Messages.tr(
                                    "status.zoom0", Messages.value(snapshot.completedZoom0Tiles())),
                                Messages.tr(
                                    "status.zoomed",
                                    Messages.value(snapshot.completedZoomTiles()))),
                            Messages.row(
                                "status.average",
                                millis("status.zoom0", snapshot.averageZoom0Millis()),
                                millis("status.zoomed", snapshot.averageZoomMillis())),
                            Messages.row(
                                "status.per_batch",
                                millis("status.sample", timing.averageSampleMillis()),
                                millis("status.region_io", timing.averageChunkReadMillis()),
                                millis("status.draw", timing.averageRenderTilesMillis()),
                                millis("status.persist", timing.averagePersistMillis())),
                            Messages.row(
                                "status.tile_cache",
                                Messages.tr("status.entries", Messages.value(cache.size())),
                                Messages.tr(
                                    "status.hit_rate",
                                    Messages.value(Formats.percent(cache.hitRate()))))))
                    .color(Messages.TEXT));

    final @NotNull Component isometric =
        Messages.tr("status.isometric", Messages.value(iso.renderedBaseTiles()))
            .hoverEvent(
                Messages.lines(
                        List.of(
                            Messages.section("status.isometric_title"),
                            Messages.row(
                                "status.average",
                                millis("unit.per_tile", iso.averageRenderMillis()),
                                Messages.tr(
                                    "status.reading_share",
                                    Messages.value(Formats.percent(iso.sampleShare())))),
                            Messages.row(
                                "status.chunk_columns",
                                Messages.tr(
                                    "status.cached", Messages.value(this.chunkColumnsCache.size())),
                                Messages.tr(
                                    "status.reused",
                                    Messages.value(Formats.percent(iso.chunkReuseRate())))),
                            Messages.row(
                                "status.redrawn",
                                Messages.tr("unit.tiles", Messages.value(iso.invalidatedTiles())))))
                    .color(Messages.TEXT));

    return Messages.row("status.tiles", topDown, isometric);
  }

  private @NotNull Component lastRender() {
    final @NotNull List<CompletedRender> recent = this.generator.renderHistory().recent();
    if (recent.isEmpty()) {
      return Messages.row("status.last_render", Messages.tr("status.no_render"));
    }
    final @NotNull CompletedRender last = recent.getFirst();
    final @NotNull List<Component> hover = new ArrayList<>();
    hover.add(
        Messages.parts(
            Messages.tr("unit.tiles", Messages.value(last.tiles())),
            Messages.tr("unit.regions", Messages.value(last.regions())),
            Messages.tr("unit.chunks", Messages.value(last.chunks())),
            Messages.value(Formats.rate(last.tilesPerSecond()))));
    if (recent.size() > 1) {
      hover.add(Component.empty());
      hover.add(Messages.section("status.earlier"));
      recent
          .subList(1, recent.size())
          .forEach(render -> hover.add(Messages.indent(summary(render))));
    }
    return Messages.row("status.last_render", summary(last))
        .hoverEvent(Messages.lines(hover).color(Messages.TEXT));
  }
}
