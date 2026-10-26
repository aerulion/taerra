package net.aerulion.taerra.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.chat.Messages;
import net.aerulion.taerra.chat.RenderFeedback;
import net.aerulion.taerra.config.ConfigEditor;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.icon.MarkerIconsImpl;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.rendering.RenderService;
import net.aerulion.taerra.storage.StorageSettings;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigSubCommand implements TaerraSubCommand {

  private static @NotNull String who(final @NotNull CommandContext<CommandSourceStack> context) {
    return context.getSource().getSender().getName();
  }

  private static @NotNull ArgumentType<?> type(final ConfigEditor.@NotNull Setting setting) {
    return switch (setting.kind()) {
      case NUMBER -> IntegerArgumentType.integer(setting.min(), setting.max());
      case TOGGLE -> BoolArgumentType.bool();
      case TEXT, SECRET -> StringArgumentType.greedyString();
      case CHOICE -> StringArgumentType.word();
    };
  }

  private final @NotNull ConfigEditor editor;
  private final @NotNull TaerraConfig config;
  private final @NotNull MarkerRegistry markers;
  private final @NotNull MarkerIconsImpl icons;
  private final @NotNull RenderFeedback renderFeedback;

  public ConfigSubCommand(
      final @NotNull ConfigEditor editor,
      final @NotNull TaerraConfig config,
      final @NotNull MarkerRegistry markers,
      final @NotNull MarkerIconsImpl icons,
      final @NotNull RenderFeedback renderFeedback) {
    super();
    this.editor = editor;
    this.config = config;
    this.markers = markers;
    this.icons = icons;
    this.renderFeedback = renderFeedback;
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
  public @NotNull List<TaerraPermission> permissions() {
    return List.of(TaerraPermission.CONFIG);
  }

  @Override
  public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
    final @NotNull LiteralArgumentBuilder<CommandSourceStack> views = Commands.literal("views");
    for (final @NotNull MapViews shown : MapViews.values()) {
      views.then(Commands.literal(shown.id()).executes(context -> setViews(context, shown)));
    }
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
                                    .executes(context -> setWorldPrivate(context, false)))
                            .then(
                                Commands.literal("players")
                                    .then(
                                        Commands.literal("private")
                                            .executes(context -> setPlayersPrivate(context, true)))
                                    .then(
                                        Commands.literal("public")
                                            .executes(
                                                context -> setPlayersPrivate(context, false))))
                            .then(
                                Commands.literal("name")
                                    .then(
                                        Commands.argument("name", StringArgumentType.greedyString())
                                            .executes(this::setWorldName)))
                            .then(
                                Commands.literal("position")
                                    .then(
                                        Commands.argument(
                                                "position", IntegerArgumentType.integer(1))
                                            .executes(this::moveWorld)))
                            .then(views)))
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
                                    .executes(context -> setLayerPrivate(context, false)))
                            .then(
                                Commands.literal("icon")
                                    .then(
                                        Commands.literal("none")
                                            .executes(context -> setLayerIcon(context, "")))
                                    .then(
                                        Commands.argument("icon", ArgumentTypes.key())
                                            .suggests(
                                                (context, suggestions) -> {
                                                  for (final MarkerIconsImpl.@NotNull Icon icon :
                                                      this.icons.all()) {
                                                    final @NotNull String key =
                                                        icon.key().asString();
                                                    if (!key.startsWith(
                                                        suggestions.getRemaining())) {
                                                      continue;
                                                    }
                                                    if (icon.label() == null) {
                                                      suggestions.suggest(key);
                                                    } else {
                                                      suggestions.suggest(
                                                          key,
                                                          MessageComponentSerializer.message()
                                                              .serialize(
                                                                  Component.text(icon.label())));
                                                    }
                                                  }
                                                  return suggestions.buildFuture();
                                                })
                                            .executes(
                                                context ->
                                                    setLayerIcon(
                                                        context,
                                                        context
                                                            .getArgument("icon", Key.class)
                                                            .asString()))))));
    ConfigEditor.SETTINGS.forEach(
        setting ->
            builder.then(
                Commands.literal(setting.key())
                    .executes(context -> show(context, setting.key()))
                    .then(
                        Commands.argument("value", type(setting))
                            .suggests(
                                setting.options().isEmpty()
                                    ? null
                                    : (context, suggestions) -> {
                                      setting.options().forEach(suggestions::suggest);
                                      return suggestions.buildFuture();
                                    })
                            .executes(
                                context ->
                                    set(
                                        context,
                                        setting.key(),
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
    lines.add(listRow("config.private_players", this.config.privatePlayers()));
    lines.add(listRow("config.private_layers", this.config.privateLayers()));
    ConfigEditor.SETTINGS.stream()
        .map(ConfigEditor.Setting::key)
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
                        .append(Messages.value(String.valueOf(this.editor.get(key))))
                        .build()));
    feedback(context, Messages.lines(lines));
    return SUCCESS;
  }

  private int show(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull String key) {
    feedback(
        context,
        Messages.info(
            "config.value",
            Messages.value(key),
            Messages.value(String.valueOf(this.editor.get(key)))));
    return SUCCESS;
  }

  private int set(
      final @NotNull CommandContext<CommandSourceStack> context,
      final @NotNull String key,
      final @NotNull Object given) {
    if (key.startsWith(ConfigEditor.STORAGE)) {
      return setStorage(context, key, given);
    }
    final ConfigEditor.@NotNull Change change = this.editor.set(key, given, who(context));
    feedback(
        context,
        switch (change.effect()) {
          case REJECTED -> Messages.error("config.bad_name");
          case WEB_RESTARTED ->
              Messages.success(
                  "config.set_web", Messages.value(key), Messages.value(change.value()));
          case APPLIED ->
              Messages.success("config.set", Messages.value(key), Messages.value(change.value()));
        });
    return SUCCESS;
  }

  private int setStorage(
      final @NotNull CommandContext<CommandSourceStack> context,
      final @NotNull String key,
      final @NotNull Object given) {
    final @NotNull StorageSettings before = this.editor.storage();
    this.editor
        .setStorage(key, given, who(context))
        .thenAccept(
            outcome ->
                feedback(
                    context,
                    switch (outcome) {
                      case ConfigEditor.Applied _ ->
                          this.editor.storage().equals(before)
                              ? Messages.success(
                                  "config.set",
                                  Messages.value(key),
                                  Messages.value(this.editor.get(key)))
                              : Messages.success(
                                  "config.set_storage",
                                  Messages.value(key),
                                  Messages.value(this.editor.get(key)),
                                  Messages.value(this.editor.storage().toString()));
                      case ConfigEditor.Unreachable(final String _, final String reason) ->
                          Messages.error("config.storage_failed", Messages.value(reason));
                      case ConfigEditor.Invalid _ ->
                          Messages.error("config.storage_invalid", Messages.value(key));
                      case ConfigEditor.Busy _, ConfigEditor.Conflicted _ ->
                          Messages.error("config.storage_busy");
                    }));
    return SUCCESS;
  }

  private int setWorldPrivate(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean hidden) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    this.editor.setWorldPrivate(worldName, hidden, who(context));
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

  private int setPlayersPrivate(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean hidden) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    this.editor.setPlayersPrivate(worldName, hidden, who(context));
    feedback(
        context,
        hidden
            ? Messages.success(
                "config.players_private",
                Messages.world(worldName),
                Messages.copyable(TaerraConfig.playersPermission(worldName)))
            : Messages.success("config.players_public", Messages.world(worldName)));
    return SUCCESS;
  }

  private int setWorldName(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    final @NotNull String given = StringArgumentType.getString(context, "name");
    final @NotNull String name = "\"\"".equals(given) ? "" : given;
    if (!this.editor.setWorldName(worldName, name, who(context))) {
      feedback(context, Messages.error("config.bad_world_name"));
      return SUCCESS;
    }
    feedback(
        context,
        name.isBlank()
            ? Messages.success("config.world_name_reset", Messages.world(worldName))
            : Messages.success(
                "config.world_named", Messages.world(worldName), Messages.value(name.strip())));
    return SUCCESS;
  }

  private int moveWorld(final @NotNull CommandContext<CommandSourceStack> context) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    final int position = IntegerArgumentType.getInteger(context, "position");
    if (!this.editor.moveWorld(worldName, position, who(context))) {
      feedback(context, Messages.error("config.world_not_enabled", Messages.world(worldName)));
      return SUCCESS;
    }
    feedback(
        context,
        Messages.success(
            "config.world_moved",
            Messages.world(worldName),
            Messages.value(this.config.enabledWorlds().indexOf(worldName) + 1)));
    return SUCCESS;
  }

  private int setLayerPrivate(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean hidden) {
    final @NotNull String layerId = StringArgumentType.getString(context, "layer");
    if (!this.editor.setLayerPrivate(layerId, hidden, who(context))) {
      feedback(context, Messages.error("config.unknown_layer", Messages.value(layerId)));
      return SUCCESS;
    }
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

  private int setLayerIcon(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull String icon) {
    final @NotNull String layerId = StringArgumentType.getString(context, "layer");
    feedback(
        context,
        switch (this.editor.setLayerIcon(layerId, icon, who(context))) {
          case SET ->
              icon.isEmpty()
                  ? Messages.success("config.layer_icon_cleared", Messages.value(layerId))
                  : Messages.success(
                      "config.layer_icon", Messages.value(layerId), Messages.value(icon));
          case UNKNOWN_LAYER -> Messages.error("config.unknown_layer", Messages.value(layerId));
          case UNKNOWN_ICON -> Messages.error("config.unknown_icon", Messages.value(icon));
        });
    return SUCCESS;
  }

  private int setViews(
      final @NotNull CommandContext<CommandSourceStack> context, final @NotNull MapViews views) {
    final @NotNull World world = context.getArgument("world", World.class);
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull Component shown = Messages.tr("config.views." + views.id());
    final RenderService.@Nullable Start start = this.editor.setViews(world, views, who(context));
    if (start == null) {
      feedback(context, Messages.success("config.world_views", Messages.world(worldName), shown));
    } else {
      this.renderFeedback.report(
          sender(context), worldName, Messages.tr("render.what.views", shown), start);
    }
    return SUCCESS;
  }

  private int setWorld(
      final @NotNull CommandContext<CommandSourceStack> context, final boolean enabled) {
    final @NotNull String worldName = WorldKeys.id(context.getArgument("world", World.class));
    this.editor.setWorldEnabled(worldName, enabled, who(context));
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
