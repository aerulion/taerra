package net.aerulion.taerra.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class TaerraCommands {

  private TaerraCommands() {
    super();
  }

  public static void register(
      final @NotNull Plugin plugin, final @NotNull List<? extends TaerraSubCommand> subCommands) {
    final @NotNull Map<LiteralCommandNode<CommandSourceStack>, List<String>> nodes =
        new LinkedHashMap<>();
    final @NotNull LiteralArgumentBuilder<CommandSourceStack> taerraCommand =
        Commands.literal("taerra");
    for (final @NotNull TaerraSubCommand subCommand : subCommands) {
      taerraCommand.then(subCommand.command());
      if (subCommand.register()) {
        nodes.put(subCommand.command().build(), subCommand.aliases());
      }
    }
    taerraCommand.executes(
        context -> {
          sendHelp(plugin, subCommands, context.getSource().getSender());
          return Command.SINGLE_SUCCESS;
        });
    nodes.put(taerraCommand.build(), List.of());
    plugin
        .getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event ->
                nodes.forEach(
                    (commandNode, aliases) -> event.registrar().register(commandNode, aliases)));
  }

  private static void sendHelp(
      final @NotNull Plugin plugin,
      final @NotNull List<? extends TaerraSubCommand> subCommands,
      final @NotNull CommandSender sender) {
    final @NotNull List<Component> lines = new ArrayList<>();
    lines.add(
        Messages.header(
            Messages.value(
                plugin
                    .getPluginMeta()
                    .getVersion()
                    .replaceFirst("^(\\d+)-(\\p{XDigit}+)$", "Build $1 ($2)"))));
    for (final @NotNull TaerraSubCommand subCommand : subCommands) {
      if (subCommand.permission().check(sender)) {
        final @NotNull Component description = Messages.tr("command." + subCommand.name());
        lines.add(
            Messages.indent(
                Messages.suggest("/taerra " + subCommand.name(), description),
                description.color(Messages.MUTED)));
      }
    }
    sender.sendMessage(Messages.lines(lines));
  }
}
