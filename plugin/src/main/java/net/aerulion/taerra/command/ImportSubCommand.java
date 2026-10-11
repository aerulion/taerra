package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.waypoint.DynmapImport;
import net.kyori.adventure.key.KeyPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ImportSubCommand implements TaerraSubCommand {

  private static final @NotNull String DYNMAP_MARKERS = "dynmap/markers.yml";

  private final @NotNull DynmapImport dynmap;

  public ImportSubCommand(final @NotNull DynmapImport dynmap) {
    super();
    this.dynmap = dynmap;
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "import";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_CONFIG;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    return builder()
        .then(
            Commands.literal("dynmap")
                .executes(context -> importDynmap(context, DYNMAP_MARKERS))
                .then(
                    Commands.argument("file", StringArgumentType.greedyString())
                        .executes(
                            context ->
                                importDynmap(
                                    context, StringArgumentType.getString(context, "file")))));
  }

  private int importDynmap(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull String path) {
    final DynmapImport.@Nullable Report report = this.dynmap.run(path);
    if (report == null) {
      feedback(context, Messages.error("import.missing", Messages.value("plugins/" + path)));
      return SUCCESS;
    }
    if (report.empty()) {
      feedback(context, Messages.info("import.none"));
      return SUCCESS;
    }
    feedback(context, Messages.success("import.done", Messages.value(report.imported())));
    if (report.groups() > 0) {
      feedback(
          context,
          Messages.info(
              "import.groups",
              Messages.value(report.groups()),
              Messages.suggest("/taerra config layer", Messages.tr("import.private_hint"))));
    }
    if (report.existing() > 0) {
      feedback(context, Messages.info("import.existing", Messages.value(report.existing())));
    }
    if (report.unknownWorlds() > 0) {
      feedback(
          context,
          Messages.warning("import.unknown_worlds", Messages.value(report.unknownWorlds())));
    }
    if (report.shapes() > 0) {
      feedback(context, Messages.info("import.shapes", Messages.value(report.shapes())));
    }
    return SUCCESS;
  }
}
