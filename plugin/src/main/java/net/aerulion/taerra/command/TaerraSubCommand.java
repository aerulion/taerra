package net.aerulion.taerra.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.util.ThrowingFunction;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public sealed interface TaerraSubCommand
    permits ConfigSubCommand,
        ImportSubCommand,
        LimitSubCommand,
        LoginSubCommand,
        LogoutSubCommand,
        RenderSubCommand,
        StatusSubCommand,
        WaypointSubCommand {

  int SUCCESS = Command.SINGLE_SUCCESS;

  @KeyPattern.Value
  @NotNull
  String name();

  default @NotNull List<String> aliases() {
    return List.of();
  }

  @NotNull
  TaerraPermission permission();

  default boolean register() {
    return false;
  }

  default @NotNull LiteralArgumentBuilder<CommandSourceStack> builder() {
    return Commands.literal(name()).requires(permission().commandRequirement());
  }

  @NotNull
  LiteralArgumentBuilder<CommandSourceStack> command();

  default @NotNull CommandSender sender(
      final @NotNull CommandContext<? extends CommandSourceStack> context) {
    return context.getSource().getSender();
  }

  default @NotNull CommandSender scopedExecutor(
      final @NotNull CommandContext<? extends CommandSourceStack> context) {
    return context.getSource().getExecutor() instanceof final @NotNull CommandSender commandSender
        ? commandSender
        : sender(context);
  }

  default int playerScope(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull ThrowingFunction<? super Player, Integer, ? extends CommandSyntaxException>
              function)
      throws CommandSyntaxException {
    if (!(context.getSource().getExecutor() instanceof final @NotNull Player player)) {
      feedback(context, Messages.error("error.player_only"));
      return SUCCESS;
    }
    return function.apply(player);
  }

  default void feedback(
      final @NotNull CommandContext<? extends CommandSourceStack> context,
      final @NotNull ComponentLike message) {
    sender(context).sendMessage(message);
  }

  default boolean isSelfReferencing(
      final @NotNull CommandContext<? extends CommandSourceStack> context) {
    return scopedExecutor(context) == sender(context);
  }
}
