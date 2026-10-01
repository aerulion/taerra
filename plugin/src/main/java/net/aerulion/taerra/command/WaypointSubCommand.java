package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.waypoint.Waypoints;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointSubCommand implements TaerraSubCommand {

  private static final @NotNull List<String> SUGGESTED_COLORS =
      List.of(
          "\"#4fa3e0\"",
          "\"#52a535\"",
          "\"#e34a4a\"",
          "\"#ffcf3f\"",
          "\"#a86fe0\"",
          "\"#f08a3c\"",
          "\"#ffffff\"");

  private final @NotNull Waypoints waypoints;

  public WaypointSubCommand(final @NotNull Waypoints waypoints) {
    super();
    this.waypoints = waypoints;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "waypoint";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_WAYPOINT;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .then(
            Commands.literal("add")
                .then(
                    Commands.argument("color", StringArgumentType.string())
                        .suggests(
                            (context, builder) -> {
                              SUGGESTED_COLORS.stream()
                                  .filter(
                                      color -> color.startsWith(builder.getRemainingLowerCase()))
                                  .forEach(builder::suggest);
                              return builder.buildFuture();
                            })
                        .then(
                            Commands.argument("name", StringArgumentType.greedyString())
                                .executes(this::add))))
        .then(
            Commands.literal("remove")
                .then(
                    Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(
                            (context, builder) -> {
                              this.waypoints.names().stream()
                                  .filter(
                                      name ->
                                          name.toLowerCase(Locale.ROOT)
                                              .startsWith(builder.getRemainingLowerCase()))
                                  .forEach(builder::suggest);
                              return builder.buildFuture();
                            })
                        .executes(this::remove)));
  }

  private int add(final @NotNull CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    return playerScope(
        context,
        player -> {
          final @Nullable String name = Names.of(StringArgumentType.getString(context, "name"));
          final @NotNull String colorText = StringArgumentType.getString(context, "color");
          final @NotNull OptionalInt rgb = HexColors.parseRgb(colorText);
          if (name == null) {
            feedback(context, Messages.error("waypoint.bad_name"));
            return SUCCESS;
          }
          if (rgb.isEmpty()) {
            feedback(
                context,
                Messages.error(
                    "waypoint.bad_color", Messages.value(colorText), Messages.value("#4fa3e0")));
            return SUCCESS;
          }
          final @NotNull Color color = Color.fromRGB(rgb.getAsInt());
          this.waypoints.add(name, player.getLocation(), color);
          feedback(
              context,
              Messages.success(
                  "waypoint.added",
                  Component.text(name, TextColor.color(color.asRGB())),
                  Messages.copyable(
                      player.getLocation().getBlockX()
                          + ", "
                          + player.getLocation().getBlockY()
                          + ", "
                          + player.getLocation().getBlockZ())));
          return SUCCESS;
        });
  }

  private int remove(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String name = StringArgumentType.getString(context, "name").strip();
    feedback(
        context,
        this.waypoints.remove(name)
            ? Messages.success("waypoint.removed", Messages.value(name))
            : Messages.error("waypoint.unknown", Messages.value(name)));
    return SUCCESS;
  }
}
