package net.aerulion.taerra.mod.gui.screen;

import net.aerulion.taerra.mod.config.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class SettingsScreen extends OptionsSubScreen {

  private final Settings settings;

  public SettingsScreen(final @NotNull Screen lastScreen, final Settings settings) {
    super(lastScreen, Minecraft.getInstance().options, Component.translatable("taerra.options"));
    this.settings = settings;
  }

  @Override
  protected void addOptions() {
    final Settings settings = this.settings;
    this.list.addSmall(
        OptionInstance.createBoolean(
            "taerra.options.minimap", settings.minimap(), settings::setMinimap),
        OptionInstance.createBoolean(
            "taerra.options.rotates",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("taerra.options.rotates.tooltip")),
            settings.minimapRotates(),
            settings::setMinimapRotates),
        new OptionInstance<>(
            "taerra.options.size",
            OptionInstance.noTooltip(),
            Options::genericValueLabel,
            new OptionInstance.IntRange(Settings.MIN_SIZE, Settings.MAX_SIZE),
            settings.minimapSize(),
            settings::setMinimapSize),
        new OptionInstance<>(
            "taerra.options.zoom",
            OptionInstance.noTooltip(),
            Options::genericValueLabel,
            new OptionInstance.IntRange(Settings.MIN_ZOOM, Settings.MAX_ZOOM),
            settings.minimapZoom(),
            settings::setMinimapZoom),
        OptionInstance.createBoolean(
            "taerra.options.side",
            OptionInstance.noTooltip(),
            (caption, left) ->
                Options.genericValueLabel(
                    caption,
                    Component.translatable(
                        left ? "taerra.options.side.left" : "taerra.options.side.right")),
            settings.minimapLeft(),
            settings::setMinimapLeft),
        OptionInstance.createBoolean(
            "taerra.options.colors",
            OptionInstance.noTooltip(),
            (caption, gray) ->
                Options.genericValueLabel(
                    caption,
                    Component.translatable(
                        gray ? "taerra.options.colors.gray" : "taerra.options.colors.color")),
            settings.grayMap(),
            settings::setGrayMap),
        OptionInstance.createBoolean(
            "taerra.options.waypoints",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("taerra.options.waypoints.tooltip")),
            settings.waypointLabels(),
            settings::setWaypointLabels));
  }

  @Override
  public void removed() {
    this.settings.save();
    super.removed();
  }
}
