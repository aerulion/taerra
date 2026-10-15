package net.aerulion.taerra.mod.gui.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.aerulion.taerra.mod.config.MinimapInfo;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.gui.hud.Minimap;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.web.WebMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingsScreen extends OptionsSubScreen {

  private final Settings settings;
  private final @Nullable WebMap map;
  private int footerWidth;

  public SettingsScreen(
      final @NotNull Screen lastScreen, final Settings settings, final @Nullable WebMap map) {
    super(lastScreen, Minecraft.getInstance().options, Component.translatable("taerra.options"));
    this.settings = settings;
    this.map = map;
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.footerWidth = this.width;
    super.init();
  }

  @Override
  protected void repositionElements() {
    if (this.footerWidth != this.width) {
      rebuildWidgets();
      return;
    }
    super.repositionElements();
  }

  @Override
  protected void addFooter() {
    final @Nullable WebMap map = this.map;
    if (map == null || map.account() == null) {
      super.addFooter();
      return;
    }
    final @NotNull List<Button> buttons = new ArrayList<>();
    buttons.add(
        Button.builder(
                Component.translatable("taerra.devices.open"),
                _ -> this.minecraft.setScreenAndShow(new DevicesScreen(this, map)))
            .build());
    if (map.account() != null && map.account().config()) {
      buttons.add(
          Button.builder(
                  Component.translatable("taerra.settings.open"),
                  _ -> this.minecraft.setScreenAndShow(new ServerSettingsScreen(this, map)))
              .build());
    }
    buttons.add(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).build());
    FooterRows.add(
        this.layout, this.width, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT, buttons);
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
            "taerra.options.offmap",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("taerra.options.offmap.tooltip")),
            (_, direction) ->
                Component.translatable(
                    direction ? "taerra.options.offmap.direction" : "taerra.options.offmap.edge"),
            settings.offMapDirection(),
            settings::setOffMapDirection),
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
    this.list.addHeader(Component.translatable("taerra.options.info"));
    this.list.addSmall(
        Arrays.stream(MinimapInfo.values())
            .map(
                info ->
                    OptionInstance.createBoolean(
                        "taerra.options.info." + info.id(),
                        Language.getInstance().has("taerra.options.info." + info.id() + ".tooltip")
                            ? OptionInstance.cachedConstantTooltip(
                                Component.translatable(
                                    "taerra.options.info." + info.id() + ".tooltip"))
                            : OptionInstance.noTooltip(),
                        settings.minimapInfo(info),
                        shown -> settings.setMinimapInfo(info, shown)))
            .toArray(OptionInstance<?>[]::new));
  }

  @Override
  public void removed() {
    this.settings.save();
    super.removed();
  }
}
