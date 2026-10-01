package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.web.http.TaerraWebServer;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigSubCommand implements TaerraSubCommand {

  private static final @NotNull Map<String, ArgumentType<?>> SETTINGS = new LinkedHashMap<>();

  static {
    SETTINGS.put("web.port", IntegerArgumentType.integer(1, 65535));
    SETTINGS.put("web.host", StringArgumentType.word());
    SETTINGS.put("web.public-url", StringArgumentType.greedyString());
    SETTINGS.put("web.name", StringArgumentType.greedyString());
    SETTINGS.put("rendering.render-threads", IntegerArgumentType.integer(0));
    SETTINGS.put("rendering.max-tiles-per-second", IntegerArgumentType.integer(0));
    SETTINGS.put("rendering.update-interval", IntegerArgumentType.integer(0));
    SETTINGS.put("cache.max-tiles", IntegerArgumentType.integer(1));
    SETTINGS.put("cache.max-chunks", IntegerArgumentType.integer(256));
    SETTINGS.put("markers.worldguard.enabled", BoolArgumentType.bool());
    SETTINGS.put("markers.worldguard.shown-by-default", BoolArgumentType.bool());
    SETTINGS.put("markers.worldborder.enabled", BoolArgumentType.bool());
    SETTINGS.put("markers.worldborder.shown-by-default", BoolArgumentType.bool());
  }

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull TaerraWebServer webServer;
  private final @NotNull MarkerRegistry markers;

  public ConfigSubCommand(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull TaerraWebServer webServer,
      final @NotNull MarkerRegistry markers) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.webServer = webServer;
    this.markers = markers;
  }

  private static @NotNull Component listRow(
      final @NotNull String labelKey, final @NotNull List<String> values) {
    return Messages.row(
        labelKey,
        values.isEmpty()
            ? Messages.tr("config.no_worlds")
            : Component.join(
                JoinConfiguration.commas(true), values.stream().map(Messages::value).toList()));
  }

  @Override
  @KeyPattern.Value
  public @NotNull String name() {
    return "config";
  }

  @Override
  public @NotNull TaerraPermission permission() {
    return TaerraPermission.COMMAND_CONFIG;
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    final @NotNull LiteralArgumentBuilder<CommandSourceStack> builder =
        builder()
            .executes(this::list)
            .then(
                Commands.literal("world")
                    .then(
                        Commands.argument("world", ArgumentTypes.world())
                            .then(
                                Commands.literal("enable")
                                    .executes(context -> setWorld(context, true)))
                            .then(
                                Commands.literal("disable")
                                    .executes(context -> setWorld(context, false)))
                            .then(
                                Commands.literal("private")
                                    .executes(context -> setWorldPrivate(context, true)))
                            .then(
                                Commands.literal("public")
                                    .executes(context -> setWorldPrivate(context, false)))))
            .then(
                Commands.literal("layer")
                    .then(
                        Commands.argument("layer", StringArgumentType.string())
                            .suggests(
                                (context, suggestions) -> {
                                  this.markers.layers().stream()
                                      .map(MarkerLayer::id)
                                      .filter(id -> id.startsWith(suggestions.getRemaining()))
                                      .forEach(suggestions::suggest);
                                  return suggestions.buildFuture();
                                })
                            .then(
                                Commands.literal("private")
                                    .executes(context -> setLayerPrivate(context, true)))
                            .then(
                                Commands.literal("public")
                                    .executes(context -> setLayerPrivate(context, false)))));
    SETTINGS.forEach(
        (key, type) ->
            builder.then(
                Commands.literal(key)
                    .executes(context -> show(context, key))
                    .then(
                        Commands.argument("value", type)
                            .executes(
                                context ->
                                    set(
                                        context,
                                        key,
                                        context.getArgument("value", Object.class))))));
    return builder;
  }

  private int list(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull List<Component> lines = new ArrayList<>();
    lines.add(Messages.header(Messages.tr("config.title")));
    lines.add(
        Messages.row(
            "config.worlds",
            this.config.enabledWorlds().isEmpty()
                ? Messages.tr("config.no_worlds")
                : Component.join(
                    JoinConfiguration.commas(true),
                    this.config.enabledWorlds().stream().map(Messages::world).toList())));
    lines.add(listRow("config.private_worlds", this.config.privateWorlds()));
    lines.add(listRow("config.private_layers", this.config.privateLayers()));
    SETTINGS
        .keySet()
        .forEach(
            key ->
                lines.add(
                    Component.text()
                        .append(Component.text("  "))
                        .append(
                            Component.text(key, Messages.MUTED)
                                .hoverEvent(Messages.tr("click.suggest").color(Messages.TEXT))
                                .clickEvent(
                                    ClickEvent.suggestCommand("/taerra config " + key + " ")))
                        .append(Component.text("  "))
                        .append(Messages.value(this.plugin.getConfig().get(key)))
                        .build()));
    feedback(context, Messages.lines(lines));
    return SUCCESS;
  }

  private int show(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull String key) {
    feedback(
        context,
        Messages.info(
            "config.value", Messages.value(key), Messages.value(this.plugin.getConfig().get(key))));
    return SUCCESS;
  }

  private int set(
      final @NotNull CommandContext<CommandSourceStack> context,
      final @NotNull String key,
      final @NotNull Object given) {
    @NotNull Object value = "\"\"".equals(given) ? "" : given;
    if (key.equals("web.name") && !value.toString().isBlank()) {
      final @Nullable String name = Names.of(value.toString());
      if (name == null) {
        feedback(context, Messages.error("config.bad_name"));
        return SUCCESS;
      }
      value = name;
    }
    this.plugin.getConfig().set(key, value);
    this.plugin.saveConfig();
    if (key.startsWith("web.")) {
      this.plugin
          .getServer()
          .getScheduler()
          .runTaskAsynchronously(this.plugin, this.webServer::restart);
      feedback(
          context, Messages.success("config.set_web", Messages.value(key), Messages.value(value)));
      return SUCCESS;
    }
    feedback(
        context,
        Messages.success("config.set_restart", Messages.value(key), Messages.value(value)));
    return SUCCESS;
  }

  private int setWorldPrivate(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean hidden) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    this.config.setWorldPrivate(this.plugin, worldName, hidden);
    feedback(
        context,
        hidden
            ? Messages.success(
                "config.world_private",
                Messages.world(worldName),
                Messages.copyable(TaerraConfig.worldPermission(worldName)))
            : Messages.success("config.world_public", Messages.world(worldName)));
    return SUCCESS;
  }

  private int setLayerPrivate(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean hidden) {
    final @NotNull String layerId = StringArgumentType.getString(context, "layer");
    if (this.markers.existingLayer(layerId) == null && !this.config.layerPrivate(layerId)) {
      feedback(context, Messages.error("config.unknown_layer", Messages.value(layerId)));
      return SUCCESS;
    }
    this.config.setLayerPrivate(this.plugin, layerId, hidden);
    this.webServer.refreshMarkers();
    feedback(
        context,
        hidden
            ? Messages.success(
                "config.layer_private",
                Messages.value(layerId),
                Messages.copyable(TaerraConfig.layerPermission(layerId)))
            : Messages.success("config.layer_public", Messages.value(layerId)));
    return SUCCESS;
  }

  private int setWorld(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean enabled) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    this.config.setWorldEnabled(this.plugin, worldName, enabled);
    feedback(
        context,
        enabled
            ? Messages.success(
                "config.world_enabled",
                Messages.world(worldName),
                Messages.command("/taerra render full " + worldName))
            : Messages.success("config.world_disabled", Messages.world(worldName)));
    return SUCCESS;
  }
}
