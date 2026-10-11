package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.chat.Formats;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.storage.MapImageExports;
import net.aerulion.taerra.storage.WorldImageExport;
import net.aerulion.taerra.util.Throwables;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderSubCommand implements TaerraSubCommand {

  private final @NotNull RenderService renders;
  private final @NotNull RenderFeedback feedback;
  private final @NotNull MapImageExports exports;

  public RenderSubCommand(
      final @NotNull RenderService renders,
      final @NotNull RenderFeedback feedback,
      final @NotNull MapImageExports exports) {
    super();
    this.renders = renders;
    this.feedback = feedback;
    this.exports = exports;
  }

  private static @NotNull Location defaultCenter(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull World world) {
    if (context.getSource().getExecutor() instanceof final @NotNull Player player
        && player.getWorld().equals(world)) {
      return player.getLocation();
    }
    return world.getSpawnLocation();
  }

  private static @NotNull World world(
      final @NotNull CommandContext<? extends CommandSourceStack> context) {
    return context.getArgument("world", World.class);
  }

  private static @NotNull Component exported(
      final @NotNull String layer, final @Nullable WorldImageExport export, final long millis) {
    return export == null
        ? Messages.warning("image.empty", Messages.value(layer))
        : Messages.success(
            "image.done",
            Messages.value(layer)
                .hoverEvent(
                    Messages.parts(
                            Messages.value(export.width() + " × " + export.height() + " px"),
                            Messages.tr("unit.tiles", Messages.value(export.tiles())),
                            Messages.value(Formats.megabytes(export.bytes())),
                            Messages.value(Formats.duration(millis)))
                        .color(Messages.TEXT)),
            Messages.copyable("exports/" + export.file().getFileName()));
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
                .then(Commands.argument("world", ArgumentTypes.world()).executes(this::full)))
        .then(
            Commands.literal("radius")
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer(1))
                                .executes(
                                    context -> {
                                      final @NotNull Location center =
                                          defaultCenter(context, world(context));
                                      return radius(
                                          context, center.getBlockX(), center.getBlockZ());
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
                                                            IntegerArgumentType.getInteger(
                                                                context, "center_x"),
                                                            IntegerArgumentType.getInteger(
                                                                context, "center_z"))))))))
        .then(
            Commands.literal("image")
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .executes(context -> image(context, 0))
                        .then(
                            Commands.argument(
                                    "zoom", IntegerArgumentType.integer(0, TileGeometry.MAX_ZOOM))
                                .executes(
                                    context ->
                                        image(
                                            context,
                                            IntegerArgumentType.getInteger(context, "zoom"))))));
  }

  private int full(final @NotNull CommandContext<? extends CommandSourceStack> context) {
    final @NotNull World world = world(context);
    this.feedback.report(
        sender(context),
        WorldKeys.id(world),
        Messages.tr("render.what.full"),
        this.renders.full(world));
    return SUCCESS;
  }

  private int radius(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final int centerX,
      final int centerZ) {
    final @NotNull World world = world(context);
    final int radius = IntegerArgumentType.getInteger(context, "radius");
    this.feedback.report(
        sender(context),
        WorldKeys.id(world),
        Messages.tr(
            "render.what.radius", Messages.value(radius), Messages.value(centerX + ", " + centerZ)),
        this.renders.radius(world, centerX, centerZ, radius));
    return SUCCESS;
  }

  private int cancel(final @NotNull CommandContext<? extends CommandSourceStack> context) {
    final @NotNull CommandSender sender = sender(context);
    this.feedback.later(
        sender,
        () -> RenderFeedback.canceled(this.renders.cancel("Canceled by " + sender.getName())));
    return SUCCESS;
  }

  private int flush(final @NotNull CommandContext<? extends CommandSourceStack> context) {
    final @NotNull CommandSender sender = sender(context);
    this.feedback.later(
        sender, () -> RenderFeedback.flushed(this.renders.flush("Flushed by " + sender.getName())));
    return SUCCESS;
  }

  private int image(
      final @NotNull CommandContext<? extends CommandSourceStack> context, final int zoom) {
    final @NotNull World world = world(context);
    final @NotNull CommandSender sender = sender(context);
    final @Nullable CompletableFuture<Void> export =
        this.exports.start(
            world,
            zoom,
            (layer, image, millis) -> this.feedback.send(sender, exported(layer, image, millis)));
    if (export == null) {
      feedback(context, Messages.error("image.busy"));
      return SUCCESS;
    }
    feedback(
        context,
        Messages.info(
            "image.started",
            Messages.value(MapImageExports.LAYERS),
            Messages.world(WorldKeys.id(world)),
            Messages.value("1/" + (1 << zoom))));
    export.exceptionally(
        error -> {
          this.feedback.send(
              sender,
              Messages.error(
                  "image.failed", Messages.value(Throwables.describe(Throwables.unwrap(error)))));
          return null;
        });
    return SUCCESS;
  }
}
