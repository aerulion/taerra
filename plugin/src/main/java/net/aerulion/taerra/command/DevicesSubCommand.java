package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.auth.Device;
import net.aerulion.taerra.web.auth.WebSessions;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DevicesSubCommand implements TaerraSubCommand {

  private final @NotNull WebSessions sessions;

  public DevicesSubCommand(final @NotNull WebSessions sessions) {
    super();
    this.sessions = sessions;
  }

  static @NotNull Component device(final WebSessions.@NotNull Active active) {
    final @Nullable Device device = active.device();
    if (active.game()) {
      return Messages.tr("devices.game");
    }
    if (device == null || device.browser() == null) {
      return device == null || device.os() == null
          ? Messages.tr("devices.unknown")
          : Messages.tr("devices.on", Messages.tr("devices.unknown"), Component.text(device.os()));
    }
    return device.os() == null
        ? Component.text(device.browser())
        : Messages.tr("devices.on", Component.text(device.browser()), Component.text(device.os()));
  }

  static @NotNull Component ago(final long millis) {
    final @NotNull Duration since = Duration.ofMillis(Math.max(0, millis));
    if (since.toMinutes() < 1) {
      return Messages.tr("devices.now");
    }
    if (since.toHours() < 1) {
      return Messages.tr("devices.minutes", Component.text(since.toMinutes()));
    }
    return since.toDays() < 1
        ? Messages.tr("devices.hours", Component.text(since.toHours()))
        : Messages.tr("devices.days", Component.text(since.toDays()));
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "devices";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_LOGIN;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .executes(this::list)
        .then(
            Commands.literal("logout")
                .then(
                    Commands.argument("device", StringArgumentType.word()).executes(this::logout)));
  }

  private int list(final @NotNull CommandContext<CommandSourceStack> context) {
    if (!(sender(context) instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    final @NotNull List<WebSessions.Active> devices =
        this.sessions.devices(player.getUniqueId(), null);
    if (devices.isEmpty()) {
      feedback(context, Messages.info("logout.none"));
      return SUCCESS;
    }
    final long now = System.currentTimeMillis();
    final @NotNull List<Component> lines = new ArrayList<>();
    lines.add(Messages.header(Messages.tr("devices.title")));
    for (final WebSessions.@NotNull Active device : devices) {
      lines.add(
          device.game()
              ? Messages.indent(device(device).color(Messages.VALUE), Messages.tr("devices.here"))
              : Messages.indent(
                  device(device).color(Messages.VALUE),
                  ago(now - device.seen()),
                  Messages.button(
                      "/taerra devices logout " + device.id(), Messages.tr("devices.logout"))));
    }
    if (devices.stream().filter(device -> !device.game()).count() > 1) {
      lines.add(
          Messages.indent(Messages.button("/taerra logout", Messages.tr("devices.logout_all"))));
    }
    feedback(context, Messages.lines(lines));
    return SUCCESS;
  }

  private int logout(final @NotNull CommandContext<CommandSourceStack> context) {
    if (!(sender(context) instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    final @NotNull String id = StringArgumentType.getString(context, "device");
    final WebSessions.@Nullable Active device =
        this.sessions.devices(player.getUniqueId(), null).stream()
            .filter(known -> known.id().equals(id))
            .findFirst()
            .orElse(null);
    feedback(
        context,
        device != null && this.sessions.endDevice(player.getUniqueId(), id)
            ? Messages.success("devices.logged_out", device(device).color(Messages.VALUE))
            : Messages.error("devices.gone"));
    return SUCCESS;
  }
}
