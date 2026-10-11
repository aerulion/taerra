package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.iso.IsoTileGeometry;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.queue.ChangeBuffer;
import net.aerulion.taerra.rendering.queue.RenderPriority;
import net.aerulion.taerra.rendering.queue.RenderQueue;
import net.aerulion.taerra.rendering.region.RegionFileScanner;
import net.aerulion.taerra.rendering.region.RegionScanListener;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.session.ManualRenderSession;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.storage.WorldImageExport;
import net.aerulion.taerra.storage.WorldImageExporter;
import net.aerulion.taerra.util.Throwables;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderSubCommand implements TaerraSubCommand {

  private static final int REGION_SIZE_BLOCKS = 512;
  private static final @NotNull DateTimeFormatter EXPORT_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final @NotNull Plugin plugin;
  private final @NotNull TileGeneratorService generator;
  private final @NotNull RenderQueue renderQueue;
  private final @NotNull ChangeBuffer changes;
  private final @NotNull TileStorage tileStorage;
  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull WorldLimits limits;
  private final @NotNull TaerraConfig config;
  private final @NotNull AtomicBoolean exporting = new AtomicBoolean();

  public RenderSubCommand(
      final @NotNull Plugin plugin,
      final @NotNull TileGeneratorService generator,
      final @NotNull RenderQueue renderQueue,
      final @NotNull ChangeBuffer changes,
      final @NotNull TileStorage tileStorage,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull WorldLimits limits,
      final @NotNull TaerraConfig config) {
    super();
    this.plugin = plugin;
    this.generator = generator;
    this.renderQueue = renderQueue;
    this.changes = changes;
    this.tileStorage = tileStorage;
    this.columns = columns;
    this.limits = limits;
    this.config = config;
  }

  private static @NotNull Location resolveDefaultCenter(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull World world) {
    if (context.getSource().getExecutor() instanceof final @NotNull Player player
        && player.getWorld().equals(world)) {
      return player.getLocation();
    }
    return world.getSpawnLocation();
  }

  private static boolean intersects(
      final @Nullable WorldLimit limit, final int x, final int z, final int size) {
    return limit == null
        || limit.intersects(x * size, z * size, x * size + size - 1, z * size + size - 1);
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "render";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_RENDER;
  }

  @Override
  public boolean register() {
    return true;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .then(Commands.literal("cancel").executes(this::cancel))
        .then(Commands.literal("flush").executes(this::flush))
        .then(
            Commands.literal("full")
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .executes(
                            context -> {
                              final @NotNull World world =
                                  context.getArgument("world", World.class);
                              start(
                                  sender(context),
                                  TileGeneratorService.FULL_RENDER,
                                  world,
                                  Messages.tr("render.what.full"),
                                  listener -> scanFull(world, listener));
                              return SUCCESS;
                            })))
        .then(
            Commands.literal("radius")
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(
                                    context -> {
                                      final @NotNull World world =
                                          context.getArgument("world", World.class);
                                      final @NotNull Location center =
                                          resolveDefaultCenter(context, world);
                                      return radius(
                                          context,
                                          world,
                                          center.getBlockX(),
                                          center.getBlockZ(),
                                          IntegerArgumentType.getInteger(context, "radius"));
                                    }))
                        .then(
                            Commands.argument("center_x", IntegerArgumentType.integer())
                                .then(
                                    Commands.argument("center_z", IntegerArgumentType.integer())
                                        .then(
                                            Commands.argument(
                                                    "radius", IntegerArgumentType.integer(1))
                                                .executes(
                                                    context ->
                                                        radius(
                                                            context,
                                                            context.getArgument(
                                                                "world", World.class),
                                                            IntegerArgumentType.getInteger(
                                                                context, "center_x"),
                                                            IntegerArgumentType.getInteger(
                                                                context, "center_z"),
                                                            IntegerArgumentType.getInteger(
                                                                context, "radius"))))))))
        .then(
            Commands.literal("image")
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .executes(
                            context -> image(context, context.getArgument("world", World.class), 0))
                        .then(
                            Commands.argument(
                                    "zoom", IntegerArgumentType.integer(0, TileGeometry.MAX_ZOOM))
                                .executes(
                                    context ->
                                        image(
                                            context,
                                            context.getArgument("world", World.class),
                                            IntegerArgumentType.getInteger(context, "zoom"))))));
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

  private int cancel(final @NotNull CommandContext<? extends CommandSourceStack> context) {
    final @NotNull ManualRenderSessionSnapshot session =
        this.generator.statusSnapshot().manualRenderSession();
    final int discarded =
        this.generator.cancelManualRender("Canceled by " + sender(context).getName());
    if (session.active() || discarded > 0) {
      this.plugin
          .getServer()
          .getScheduler()
          .runTaskAsynchronously(this.plugin, this.renderQueue::save);
      feedback(
          context,
          withLimitNote(
              session,
              Messages.success(
                  "render.canceled", Messages.tr("unit.tiles", Messages.value(discarded)))));
    } else {
      feedback(context, Messages.error("render.idle"));
    }
    return SUCCESS;
  }

  private int flush(final @NotNull CommandContext<? extends CommandSourceStack> context) {
    final @NotNull ManualRenderSessionSnapshot session =
        this.generator.statusSnapshot().manualRenderSession();
    final int discarded =
        this.generator.cancelManualRender("Flushed by " + sender(context).getName())
            + this.renderQueue.discardQueued(RenderPriority.LIVE_ZOOM_0)
            + this.changes.discard();
    this.plugin
        .getServer()
        .getScheduler()
        .runTaskAsynchronously(this.plugin, this.renderQueue::save);
    feedback(
        context,
        withLimitNote(
            session,
            Messages.success(
                "render.flushed", Messages.tr("unit.tiles", Messages.value(discarded)))));
    return SUCCESS;
  }

  private int image(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull World world,
      final int zoom) {
    if (!this.exporting.compareAndSet(false, true)) {
      feedback(context, Messages.error("image.busy"));
      return SUCCESS;
    }

    final @NotNull List<TileCoordinate> layers = new ArrayList<>();
    layers.add(TileCoordinate.topDown(WorldKeys.id(world), zoom, 0, 0));
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      layers.add(TileCoordinate.isometric(WorldKeys.id(world), rotation, zoom, 0, 0));
    }
    feedback(
        context,
        Messages.info(
            "image.started",
            Messages.value(layers.size()),
            Messages.world(WorldKeys.id(world)),
            Messages.value("1/" + (1 << zoom))));

    this.plugin
        .getServer()
        .getScheduler()
        .runTaskAsynchronously(
            this.plugin,
            () -> {
              try {
                final @NotNull Path directory =
                    this.plugin.getDataFolder().toPath().resolve("exports");
                Files.createDirectories(directory);
                final @NotNull String prefix =
                    WorldKeys.id(world).replaceAll("[^a-zA-Z0-9_-]", "_")
                        + "-"
                        + LocalDateTime.now().format(EXPORT_STAMP);
                for (final @NotNull TileCoordinate layer : layers) {
                  final @NotNull String name =
                      (layer.view().rotated()
                              ? layer.view().id() + "-" + layer.rotation().id()
                              : layer.view().id())
                          + (zoom > 0 ? "-z" + zoom : "");
                  final long startedAt = System.nanoTime();
                  final @Nullable WorldImageExport export =
                      WorldImageExporter.export(
                          this.tileStorage, layer, directory.resolve(prefix + "-" + name + ".png"));
                  sendSync(
                      context,
                      export == null
                          ? Messages.warning("image.empty", Messages.value(name))
                          : Messages.success(
                              "image.done",
                              Messages.value(name)
                                  .hoverEvent(
                                      Messages.parts(
                                              Messages.value(
                                                  export.width() + " × " + export.height() + " px"),
                                              Messages.tr(
                                                  "unit.tiles", Messages.value(export.tiles())),
                                              Messages.value(
                                                  RenderReport.megabytes(export.bytes())),
                                              Messages.value(
                                                  RenderReport.duration(
                                                      (System.nanoTime() - startedAt)
                                                          / 1_000_000L)))
                                          .color(Messages.TEXT)),
                              Messages.copyable("exports/" + export.file().getFileName())));
                }
              } catch (final Exception e) {
                final @NotNull String failure = Throwables.describe(e);
                sendSync(context, Messages.error("image.failed", Messages.value(failure)));
                this.plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "Failed to export images of " + WorldKeys.id(world) + ": " + failure,
                        e);
              } finally {
                this.exporting.set(false);
              }
            });
    return SUCCESS;
  }

  private int radius(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull World world,
      final int centerX,
      final int centerZ,
      final int radiusBlocks) {
    final int minRegionX = Math.floorDiv(centerX - radiusBlocks, REGION_SIZE_BLOCKS);
    final int maxRegionX = Math.floorDiv(centerX + radiusBlocks, REGION_SIZE_BLOCKS);
    final int minRegionZ = Math.floorDiv(centerZ - radiusBlocks, REGION_SIZE_BLOCKS);
    final int maxRegionZ = Math.floorDiv(centerZ + radiusBlocks, REGION_SIZE_BLOCKS);
    final @NotNull WorldLimit circle = WorldLimit.circle(centerX, centerZ, radiusBlocks);
    final @Nullable WorldLimit limit = this.limits.get(WorldKeys.id(world));
    start(
        sender(context),
        "radius_render",
        world,
        Messages.tr(
            "render.what.radius",
            Messages.value(radiusBlocks),
            Messages.value(centerX + ", " + centerZ)),
        listener ->
            RegionFileScanner.scanWorld(
                world,
                regionCoordinate ->
                    regionCoordinate.x() >= minRegionX
                        && regionCoordinate.x() <= maxRegionX
                        && regionCoordinate.z() >= minRegionZ
                        && regionCoordinate.z() <= maxRegionZ
                        && intersects(
                            limit, regionCoordinate.x(), regionCoordinate.z(), REGION_SIZE_BLOCKS),
                tileCoordinate ->
                    intersects(
                            circle, tileCoordinate.x(), tileCoordinate.z(), TileGeometry.TILE_SIZE)
                        && intersects(
                            limit, tileCoordinate.x(), tileCoordinate.z(), TileGeometry.TILE_SIZE),
                listener));
    return SUCCESS;
  }

  public boolean chunks(
      final @NotNull Audience audience,
      final @NotNull World world,
      final @NotNull Set<Long> chunks,
      final @Nullable Boolean hide) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    final @NotNull Set<Long> regions = new HashSet<>();
    final @NotNull Set<Long> tiles = new HashSet<>();
    final @NotNull Set<TileCoordinate> isometric = new LinkedHashSet<>();
    for (final long chunk : chunks) {
      final int blockX = ChunkCoordinate.keyX(chunk) << 4;
      final int blockZ = ChunkCoordinate.keyZ(chunk) << 4;
      regions.add(
          ChunkCoordinate.key(
              Math.floorDiv(blockX, REGION_SIZE_BLOCKS),
              Math.floorDiv(blockZ, REGION_SIZE_BLOCKS)));
      tiles.add(
          ChunkCoordinate.key(
              Math.floorDiv(blockX, TileGeometry.TILE_SIZE),
              Math.floorDiv(blockZ, TileGeometry.TILE_SIZE)));
      for (final @NotNull MapRotation rotation : MapRotation.values()) {
        IsoTileGeometry.forEachTileCovering(
            blockX,
            blockZ,
            16,
            world.getMinHeight(),
            world.getMaxHeight(),
            rotation,
            (tileX, tileY) ->
                isometric.add(TileCoordinate.isometric(worldName, rotation, 0, tileX, tileY)));
      }
    }
    final @NotNull Component amount = Messages.tr("unit.chunks", Messages.value(chunks.size()));
    return start(
        audience,
        hide == null ? "area_render" : "hide_render",
        world,
        hide == null
            ? amount
            : Messages.tr(hide ? "render.what.hide" : "render.what.reveal", amount),
        listener -> {
          if (hide != null) {
            this.limits.setHidden(worldName, chunks, hide);
          }
          this.columns.forget(worldName, chunks);
          this.renderQueue.enqueueAll(isometric, RenderPriority.FULL_RENDER_ZOOM_0);
          return RegionFileScanner.scanWorld(
              world,
              region -> regions.contains(ChunkCoordinate.key(region.x(), region.z())),
              tile ->
                  tiles.contains(ChunkCoordinate.key(tile.x(), tile.z()))
                      && intersects(limit, tile.x(), tile.z(), TileGeometry.TILE_SIZE),
              listener);
        });
  }

  @NotNull
  RegionScanResult scanFull(
      final @NotNull World world, final @NotNull RegionScanListener listener) {
    final @NotNull String worldName = WorldKeys.id(world);
    this.columns.forgetWorld(worldName);
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    return RegionFileScanner.scanWorld(
        world,
        region -> intersects(limit, region.x(), region.z(), REGION_SIZE_BLOCKS),
        tile -> intersects(limit, tile.x(), tile.z(), TileGeometry.TILE_SIZE),
        listener);
  }

  boolean start(
      final @NotNull Audience audience,
      final @NotNull String mode,
      final @NotNull World world,
      final @NotNull Component description,
      final @NotNull Function<RegionScanListener, RegionScanResult> scan) {
    final @NotNull String worldName = WorldKeys.id(world);
    if (!this.config.worldEnabled(worldName)) {
      audience.sendMessage(
          Messages.error(
              "render.world_disabled",
              Messages.world(worldName),
              Messages.command("/taerra config world " + worldName + " enable")));
      return false;
    }
    final @Nullable ManualRenderSession session =
        this.generator.tryBeginManualRender(mode, worldName);
    if (session == null) {
      audience.sendMessage(
          Messages.error(
              "render.busy",
              Messages.command("/taerra status"),
              Messages.command("/taerra render cancel")));
      return false;
    }

    audience.sendMessage(Messages.info("render.scanning", Messages.world(worldName), description));

    CompletableFuture.runAsync(
        () -> {
          try {
            final @NotNull RegionScanResult scanResult =
                scan.apply(
                    progress -> this.generator.updateManualRenderScanProgress(session, progress));
            final @NotNull List<Component> lines = new ArrayList<>();

            if (scanResult.zoom0Tiles().isEmpty()) {
              this.generator.cancelManualRender(session, "Nothing to render");
              lines.add(Messages.error("render.nothing", Messages.world(worldName)));
            } else {
              final int queuedTiles =
                  this.generator.submitManualRender(session, scanResult, "Queued " + mode);
              if (queuedTiles == 0) {
                return;
              }
              this.renderQueue.save();
              lines.add(
                  Messages.success(
                      "render.queued",
                      Messages.tr("unit.tiles", Messages.value(queuedTiles))
                          .hoverEvent(
                              Messages.lines(
                                      List.of(
                                          Messages.parts(
                                              Messages.tr(
                                                  "unit.regions",
                                                  Messages.value(scanResult.totalRegions())),
                                              Messages.tr(
                                                  "unit.chunks",
                                                  Messages.value(scanResult.discoveredChunks()))),
                                          Messages.tr("render.isometric_follows")))
                                  .color(Messages.TEXT)),
                      Messages.world(worldName),
                      Messages.command("/taerra status")));
            }
            if (scanResult.unreadableRegions() > 0) {
              lines.add(
                  Messages.warning(
                      "render.unreadable", Messages.value(scanResult.unreadableRegions())));
            }
            sendSync(audience, Messages.lines(lines));
          } catch (final Throwable e) {
            final @NotNull String failure = Throwables.describe(e);
            this.generator.cancelManualRender(session, "Render failed: " + failure);
            sendSync(
                audience,
                Messages.error(
                    "render.failed", Messages.world(worldName), Messages.value(failure)));
            this.plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to render " + worldName + ": " + failure, e);
          }
        });

    return true;
  }

  private void sendSync(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull Component message) {
    sendSync(sender(context), message);
  }

  private void sendSync(final @NotNull Audience audience, final @NotNull Component message) {
    this.plugin
        .getServer()
        .getScheduler()
        .runTask(this.plugin, () -> audience.sendMessage(message));
  }
}
