package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.position.ColumnBlockPosition;
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver;
import java.util.Locale;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LimitSubCommand implements TaerraSubCommand {

  private final @NotNull RenderSubCommand render;
  private final @NotNull WorldLimits limits;

  public LimitSubCommand(
      final @NotNull RenderSubCommand render, final @NotNull WorldLimits limits) {
    super();
    this.render = render;
    this.limits = limits;
  }

  private static @NotNull ColumnBlockPosition column(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull String name)
      throws CommandSyntaxException {
    return context
        .getArgument(name, ColumnBlockPositionResolver.class)
        .resolve(context.getSource());
  }

  private static @NotNull World world(final @NotNull CommandContext<CommandSourceStack> context) {
    return context.getArgument("world", World.class);
  }

  private static @NotNull Component describe(final @NotNull WorldLimit limit) {
    return switch (limit.shape()) {
      case CIRCLE, SQUARE ->
          Messages.tr(
              "limit." + limit.shape().name().toLowerCase(Locale.ROOT),
              Messages.value(limit.radius()),
              Messages.value(limit.centerX() + ", " + limit.centerZ()));
      case RECTANGLE ->
          Messages.tr(
              "limit.rectangle",
              Messages.value(limit.minX() + ", " + limit.minZ()),
              Messages.value(limit.maxX() + ", " + limit.maxZ()));
    };
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "limit";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_LIMIT;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .then(
            Commands.argument("world", ArgumentTypes.world())
                .executes(this::show)
                .then(
                    Commands.literal("circle")
                        .then(
                            Commands.argument("center", ArgumentTypes.columnBlockPosition())
                                .then(
                                    Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(
                                            context -> {
                                              final @NotNull ColumnBlockPosition center =
                                                  column(context, "center");
                                              return apply(
                                                  context,
                                                  WorldLimit.circle(
                                                      center.blockX(),
                                                      center.blockZ(),
                                                      IntegerArgumentType.getInteger(
                                                          context, "radius")));
                                            }))))
                .then(
                    Commands.literal("square")
                        .then(
                            Commands.argument("center", ArgumentTypes.columnBlockPosition())
                                .then(
                                    Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(
                                            context -> {
                                              final @NotNull ColumnBlockPosition center =
                                                  column(context, "center");
                                              return apply(
                                                  context,
                                                  WorldLimit.square(
                                                      center.blockX(),
                                                      center.blockZ(),
                                                      IntegerArgumentType.getInteger(
                                                          context, "radius")));
                                            }))))
                .then(
                    Commands.literal("rectangle")
                        .then(
                            Commands.argument("from", ArgumentTypes.columnBlockPosition())
                                .then(
                                    Commands.argument("to", ArgumentTypes.columnBlockPosition())
                                        .executes(
                                            context -> {
                                              final @NotNull ColumnBlockPosition from =
                                                  column(context, "from");
                                              final @NotNull ColumnBlockPosition to =
                                                  column(context, "to");
                                              return apply(
                                                  context,
                                                  WorldLimit.rectangle(
                                                      from.blockX(),
                                                      from.blockZ(),
                                                      to.blockX(),
                                                      to.blockZ()));
                                            }))))
                .then(Commands.literal("remove").executes(context -> apply(context, null))));
  }

  private int show(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String worldName = WorldKeys.id(world(context));
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    feedback(
        context,
        limit == null
            ? Messages.info("limit.none", Messages.world(worldName))
            : Messages.info("limit.current", Messages.world(worldName), describe(limit)));
    return SUCCESS;
  }

  private int apply(
      final @NotNull CommandContext<CommandSourceStack> context, final @Nullable WorldLimit limit) {
    final @NotNull World world = world(context);
    final @NotNull String worldName = WorldKeys.id(world);
    this.render.start(
        sender(context),
        TileGeneratorService.LIMIT_RENDER,
        world,
        limit == null
            ? Messages.tr("render.what.unlimited")
            : Messages.tr("render.what.limited", describe(limit)),
        listener -> {
          this.limits.set(worldName, limit);
          return this.render.scanFull(world, listener);
        });
    return SUCCESS;
  }
}
