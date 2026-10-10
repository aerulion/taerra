package net.aerulion.taerra.mod.gui.screen;

import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.gui.hud.Minimap;
import net.aerulion.taerra.mod.web.WebMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingsScreen extends OptionsSubScreen {

  private final Settings settings;
  private final @Nullable WebMap map;

  public SettingsScreen(
      final @NotNull Screen lastScreen, final Settings settings, final @Nullable WebMap map) {
    super(lastScreen, Minecraft.getInstance().options, Component.translatable("taerra.options"));
    this.settings = settings;
    this.map = map;
  }

  @Override
  protected void addFooter() {
    final @Nullable WebMap map = this.map;
    if (map == null || map.account() == null) {
      super.addFooter();
      return;
    }
    final @NotNull LinearLayout footer =
        this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
    footer.addChild(
        Button.builder(
                Component.translatable("taerra.devices.open"),
                _ -> this.minecraft.setScreenAndShow(new DevicesScreen(this, map)))
            .build());
    footer.addChild(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).build());
  }

  @Override
  protected void addOptions() {
    final Settings settings = this.settings;
    final int closest = Minimap.closestZoom(this.map);
    final int farthest = Minimap.farthestZoom(this.map);
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
            new OptionInstance.IntRange(farthest, closest),
            Math.clamp(settings.minimapZoom(), farthest, closest),
            settings::setMinimapZoom),
        OptionInstance.createBoolean(
            "taerra.options.side",
            OptionInstance.noTooltip(),
            (_, left) ->
                Component.translatable(
                    left ? "taerra.options.side.left" : "taerra.options.side.right"),
            settings.minimapLeft(),
            settings::setMinimapLeft),
        OptionInstance.createBoolean(
            "taerra.options.colors",
            OptionInstance.noTooltip(),
            (_, gray) ->
                Component.translatable(
                    gray ? "taerra.options.colors.gray" : "taerra.options.colors.color"),
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
