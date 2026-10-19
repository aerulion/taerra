package net.aerulion.taerra.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.util.Throwables;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderFeedback {

  private final @NotNull Plugin plugin;

  public RenderFeedback(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
  }

  public static @NotNull Component full() {
    return Messages.tr("render.what.full");
  }

  public static @NotNull Component area(final int chunks) {
    return Messages.tr("unit.chunks", Messages.value(chunks));
  }

  public static @NotNull Component region(final @NotNull String name) {
    return name.isEmpty()
        ? Messages.tr("render.what.region_unnamed")
        : Messages.tr("render.what.region", Messages.value(name));
  }

  public static @NotNull Component canceled(final RenderService.@NotNull Stop stop) {
    return stop.idle()
        ? Messages.error("render.idle")
        : withLimitNote(
            stop.session(),
            Messages.success(
                "render.canceled", Messages.tr("unit.tiles", Messages.value(stop.discarded()))));
  }

  public static @NotNull Component flushed(final RenderService.@NotNull Stop stop) {
    return withLimitNote(
        stop.session(),
        Messages.success(
            "render.flushed", Messages.tr("unit.tiles", Messages.value(stop.discarded()))));
  }

  private static @NotNull Component withLimitNote(
      final @NotNull ManualRenderSessionSnapshot session, final @NotNull Component message) {
    if (!session.active() || !TileGeneratorService.LIMIT_RENDER.equals(session.mode())) {
      return message;
    }
    return Messages.lines(
        List.of(
            message,
            Messages.warning(
                "render.limit_kept",
                Messages.command("/taerra render full " + session.worldName()))));
  }

  private static @NotNull Component refusal(
      final RenderService.@NotNull Refusal refusal, final @NotNull String worldName) {
    return switch (refusal) {
      case WORLD_DISABLED ->
          Messages.error(
              "render.world_disabled",
              Messages.world(worldName),
              Messages.command("/taerra config world " + worldName + " enable"));
      case BUSY ->
          Messages.error(
              "render.busy",
              Messages.command("/taerra status"),
              Messages.command("/taerra render cancel"));
    };
  }

  private static @Nullable Component scanned(
      final @NotNull String worldName, final RenderService.@NotNull Scan scan) {
    final @NotNull RegionScanResult result = scan.result();
    final @NotNull List<Component> lines = new ArrayList<>();
    switch (scan.outcome()) {
      case CANCELED -> {
        return null;
      }
      case NOTHING -> lines.add(Messages.error("render.nothing", Messages.world(worldName)));
      case QUEUED ->
          lines.add(
              Messages.success(
                  "render.queued",
                  Messages.tr("unit.tiles", Messages.value(scan.queuedTiles()))
                      .hoverEvent(
                          Messages.lines(
                                  List.of(
                                      Messages.parts(
                                          Messages.tr(
                                              "unit.regions",
                                              Messages.value(result.totalRegions())),
                                          Messages.tr(
                                              "unit.chunks",
                                              Messages.value(result.discoveredChunks()))),
                                      Messages.tr("render.isometric_follows")))
                              .color(Messages.TEXT)),
                  Messages.world(worldName),
                  Messages.command("/taerra status")));
    }
    if (result.unreadableRegions() > 0) {
      lines.add(Messages.warning("render.unreadable", Messages.value(result.unreadableRegions())));
    }
    return Messages.lines(lines);
  }

  public boolean report(
      final @NotNull Audience audience,
      final @NotNull String worldName,
      final @NotNull Component description,
      final RenderService.@NotNull Start start) {
    return switch (start) {
      case RenderService.Refused refused -> {
        audience.sendMessage(refusal(refused.reason(), worldName));
        yield false;
      }
      case RenderService.Started started -> {
        audience.sendMessage(
            Messages.info("render.scanning", Messages.world(worldName), description));
        started
            .scan()
            .whenComplete(
                (scan, error) -> {
                  final @Nullable Component message =
                      error == null
                          ? scanned(worldName, scan)
                          : Messages.error(
                              "render.failed",
                              Messages.world(worldName),
                              Messages.value(Throwables.describe(Throwables.unwrap(error))));
                  if (message != null) {
                    send(audience, message);
                  }
                });
        yield true;
      }
    };
  }

  public void later(final @NotNull Audience audience, final @NotNull Supplier<Component> message) {
    this.plugin
        .getServer()
        .getScheduler()
        .runTaskAsynchronously(this.plugin, () -> send(audience, message.get()));
  }

  public void send(final @NotNull Audience audience, final @NotNull Component message) {
    this.plugin
        .getServer()
        .getScheduler()
        .runTask(this.plugin, () -> audience.sendMessage(message));
  }
}
