package net.aerulion.taerra.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.aerulion.taerra.chat.Formats;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModSubCommand implements TaerraSubCommand {

  private final @NotNull ModHandshake handshake;

  public ModSubCommand(final @NotNull ModHandshake handshake) {
    super();
    this.handshake = handshake;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "mod";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_MOD;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder().executes(this::list);
  }

  private int list(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull Collection<? extends Player> online = Bukkit.getOnlinePlayers();
    final @NotNull List<? extends Player> players =
        online.stream()
            .filter(ModHandshake::uses)
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    if (players.isEmpty()) {
      feedback(context, Messages.info("mod.none"));
      return SUCCESS;
    }
    final @NotNull List<Component> lines = new ArrayList<>();
    lines.add(Messages.header(Messages.tr("mod.title")));
    lines.add(
        Messages.indent(
            Messages.tr(
                "mod.count", Messages.value(players.size()), Messages.value(online.size()))));
    for (final @NotNull Player player : players) {
      final @Nullable String version = this.handshake.version(player);
      lines.add(
          Messages.indent(
              Messages.value(player.getName()),
              version == null
                  ? Messages.tr("mod.unknown").color(Messages.MUTED)
                  : Component.text(Formats.build(version), Messages.MUTED)));
    }
    feedback(context, Messages.lines(lines));
    return SUCCESS;
  }
}
