package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.auth.WebSessions;
import net.kyori.adventure.key.KeyPattern;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LogoutSubCommand implements TaerraSubCommand {

  private final @NotNull WebSessions sessions;

  public LogoutSubCommand(final @NotNull WebSessions sessions) {
    super();
    this.sessions = sessions;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "logout";
  }

  @Override
  public @NotNull List<TaerraPermission> permissions() {
    return List.of(TaerraPermission.LOGIN);
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .executes(this::logout)
        .then(
            Commands.argument("player", StringArgumentType.word())
                .requires(TaerraPermission.LOGOUT_OTHERS.commandRequirement())
                .suggests(
                    (context, suggestions) -> {
                      this.sessions.accountNames().keySet().stream()
                          .filter(
                              name ->
                                  name.toLowerCase(Locale.ROOT)
                                      .startsWith(suggestions.getRemainingLowerCase()))
                          .forEach(suggestions::suggest);
                      return suggestions.buildFuture();
                    })
                .executes(this::logoutOther));
  }

  private int logoutOther(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String name = StringArgumentType.getString(context, "player");
    final @Nullable UUID uuid =
        this.sessions.accountNames().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    feedback(
        context,
        uuid != null && this.sessions.endAll(uuid) > 0
            ? Messages.success("logout.other", Messages.value(name))
            : Messages.info("logout.other_none", Messages.value(name)));
    return SUCCESS;
  }

  private int logout(final @NotNull CommandContext<CommandSourceStack> context) {
    if (!(sender(context) instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    feedback(
        context,
        this.sessions.endAll(player.getUniqueId()) > 0
            ? Messages.success("logout.done")
            : Messages.info("logout.none"));
    return SUCCESS;
  }
}
