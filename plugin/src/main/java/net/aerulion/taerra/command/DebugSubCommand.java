package net.aerulion.taerra.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.diagnostics.DebugReport;
import net.aerulion.taerra.permission.TaerraPermission;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DebugSubCommand implements TaerraSubCommand {

  private final @NotNull DebugReport report;

  public DebugSubCommand(final @NotNull DebugReport report) {
    super();
    this.report = report;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "debug";
  }

  @Override
  public @NotNull List<TaerraPermission> permissions() {
    return List.of(TaerraPermission.DEBUG);
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder().executes(this::show);
  }

  private int show(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String text = this.report.text();
    if (!(sender(context) instanceof Player)) {
      feedback(context, Component.text(text));
      return SUCCESS;
    }
    final @NotNull List<Component> lines = new ArrayList<>();
    lines.add(Messages.header(Messages.tr("debug.title")));
    for (final @NotNull String line : text.lines().skip(1).toList()) {
      lines.add(Messages.indent(Component.text(line, Messages.MUTED)));
    }
    lines.add(
        Messages.indent(
            Messages.copyButton(text, Messages.tr("debug.copy")),
            Messages.link(
                DebugReport.issueUrl(DebugReport.PLUGIN_BUG, text), Messages.tr("debug.report"))));
    feedback(context, Messages.lines(lines));
    return SUCCESS;
  }
}
