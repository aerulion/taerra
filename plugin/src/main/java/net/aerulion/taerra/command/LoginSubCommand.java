package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.auth.WebGrants;
import net.aerulion.taerra.web.auth.WebSessions;
import net.aerulion.taerra.web.player.PlayerAvatars;
import net.kyori.adventure.key.KeyPattern;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoginSubCommand implements TaerraSubCommand {

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull WebSessions sessions;

  public LoginSubCommand(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.sessions = sessions;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "login";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_LOGIN;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .executes(this::login)
        .then(Commands.argument("code", StringArgumentType.greedyString()).executes(this::pair));
  }

  private @NotNull String localUrl() {
    final @NotNull String ip = this.plugin.getServer().getIp();
    return "http://"
        + (ip.isBlank() ? "localhost" : ip)
        + ":"
        + this.plugin.getConfig().getInt("web.port", 8100);
  }

  private int pair(final @NotNull CommandContext<CommandSourceStack> context) {
    if (!(sender(context) instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    final @NotNull String typed = StringArgumentType.getString(context, "code").strip();
    final @Nullable String code = WebSessions.pairCode(typed);
    final WebSessions.@NotNull Approval approval =
        this.sessions.approve(
            code, player.getUniqueId(), player.getName(), PlayerAvatars.skin(player));
    if (approval != WebSessions.Approval.APPROVED) {
      feedback(
          context,
          approval == WebSessions.Approval.TOO_MANY
              ? Messages.error("login.too_many_codes")
              : Messages.error("login.unknown_code", Messages.value(typed)));
      return SUCCESS;
    }
    this.sessions.grant(player.getUniqueId(), WebGrants.of(player, this.config));
    feedback(context, Messages.success("login.paired", Messages.value(code)));
    return SUCCESS;
  }

  private int login(final @NotNull CommandContext<CommandSourceStack> context) {
    if (!(sender(context) instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    final @NotNull String code =
        this.sessions.createCode(
            player.getUniqueId(), player.getName(), PlayerAvatars.skin(player));
    this.sessions.grant(player.getUniqueId(), WebGrants.of(player, this.config));
    final @NotNull String configured =
        this.plugin.getConfig().getString("web.public-url", "").strip().replaceAll("/+$", "");
    feedback(
        context,
        Messages.info(
            "login.link",
            Messages.link(
                (configured.isEmpty() ? localUrl() : configured) + "/#login=" + code,
                Messages.tr("login.open"))));
    if (configured.isEmpty() && TaerraPermission.COMMAND_CONFIG.check(player)) {
      feedback(
          context,
          Messages.warning(
              "login.no_public_url",
              Messages.suggest(
                  "/taerra config web.public-url", Messages.tr("login.public_url_hint"))));
    }
    return SUCCESS;
  }
}
