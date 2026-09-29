package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.waypoint.DynmapMarkers;
import net.aerulion.taerra.waypoint.Waypoints;
import net.kyori.adventure.key.KeyPattern;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ImportSubCommand implements TaerraSubCommand {

  private static final @NotNull String DYNMAP_MARKERS = "dynmap/markers.yml";

  private final @NotNull Plugin plugin;
  private final @NotNull Waypoints waypoints;

  public ImportSubCommand(final @NotNull Plugin plugin, final @NotNull Waypoints waypoints) {
    super();
    this.plugin = plugin;
    this.waypoints = waypoints;
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
    final @NotNull File file = new File(this.plugin.getDataFolder().getParentFile(), path);
    if (!file.isFile()) {
      feedback(context, Messages.error("import.missing", Messages.value("plugins/" + path)));
      return SUCCESS;
    }
    final DynmapMarkers.@NotNull Parsed parsed =
        DynmapMarkers.parse(YamlConfiguration.loadConfiguration(file));
    final @NotNull Set<String> taken = new HashSet<>();
    this.waypoints.names().forEach(name -> taken.add(name.toLowerCase(Locale.ROOT)));
    final @NotNull List<Waypoints.Waypoint> imported = new ArrayList<>();
    final @NotNull Set<String> groups = new HashSet<>();
    int existing = 0;
    int unknownWorlds = 0;
    for (final DynmapMarkers.@NotNull Found found : parsed.points()) {
      final @Nullable World world = this.plugin.getServer().getWorld(found.world());
      if (world == null) {
        unknownWorlds++;
      } else if (!taken.add(found.name().toLowerCase(Locale.ROOT))) {
        existing++;
      } else {
        imported.add(
            new Waypoints.Waypoint(
                found.name(),
                new Location(world, found.x(), found.y(), found.z()),
                Color.fromRGB(found.rgb()),
                found.group()));
        final @Nullable String groupId = Waypoints.groupLayerId(found.group());
        if (groupId != null) {
          groups.add(groupId);
        }
      }
    }
    parsed.hiddenGroups().forEach(this.waypoints::hideByDefault);
    this.waypoints.addAll(imported);

    if (parsed.points().isEmpty() && parsed.shapes() == 0) {
      feedback(context, Messages.info("import.none"));
      return SUCCESS;
    }
    feedback(context, Messages.success("import.done", Messages.value(imported.size())));
    if (!groups.isEmpty()) {
      feedback(
          context,
          Messages.info(
              "import.groups",
              Messages.value(groups.size()),
              Messages.suggest("/taerra config layer", Messages.tr("import.private_hint"))));
    }
    if (existing > 0) {
      feedback(context, Messages.info("import.existing", Messages.value(existing)));
    }
    if (unknownWorlds > 0) {
      feedback(context, Messages.warning("import.unknown_worlds", Messages.value(unknownWorlds)));
    }
    if (parsed.shapes() > 0) {
      feedback(context, Messages.info("import.shapes", Messages.value(parsed.shapes())));
    }
    return SUCCESS;
  }
}
