package net.aerulion.taerra.mod.gui.screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.gui.widget.FooterRows;
import net.aerulion.taerra.mod.gui.widget.IconTiles;
import net.aerulion.taerra.mod.web.WebMap;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconPickerScreen extends Screen {

  private static final int MAX_COLUMNS = 12;

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private final @NotNull String chosen;
  private final @NotNull Supplier<@Nullable Identifier> fallback;
  private final @NotNull Component fallbackLabel;
  private final @NotNull Consumer<String> picked;
  private int footerWidth;
  private @NotNull String query = "";

  public IconPickerScreen(
      final @NotNull Screen parent,
      final @NotNull WebMap map,
      final @NotNull Component title,
      final @NotNull String chosen,
      final @NotNull Supplier<@Nullable Identifier> fallback,
      final @NotNull Component fallbackLabel,
      final @NotNull Consumer<String> picked) {
    super(title);
    this.parent = parent;
    this.map = map;
    this.chosen = chosen;
    this.fallback = fallback;
    this.fallbackLabel = fallbackLabel;
    this.picked = picked;
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.layout.addTitleHeader(this.title, this.font);
    this.footerWidth = this.width;
    FooterRows.add(
        this.layout,
        this.width,
        HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT,
        List.of(Button.builder(CommonComponents.GUI_CANCEL, _ -> onClose()).build()));
    final int columns = Math.clamp((this.width - 40) / IconTiles.TILE, 4, MAX_COLUMNS);
    final int rows =
        Math.max(
            1,
            (this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - 32)
                / IconTiles.TILE);
    final @NotNull LinearLayout contents =
        this.layout.addToContents(LinearLayout.vertical().spacing(4));
    final @NotNull EditBox filter =
        contents.addChild(
            new EditBox(
                this.font,
                columns * IconTiles.TILE,
                20,
                Component.translatable("taerra.waypoints.filter")));
    filter.setHint(Component.translatable("taerra.waypoints.filter"));
    final @NotNull IconTiles tiles =
        contents.addChild(
            new IconTiles(
                columns,
                rows,
                this.map,
                this.chosen,
                this.fallback,
                this.fallbackLabel,
                id -> {
                  this.picked.accept(id);
                  onClose();
                }));
    filter.setValue(this.query);
    tiles.filter(this.query);
    filter.setResponder(
        value -> {
          this.query = value;
          tiles.filter(value);
        });
    this.layout.visitWidgets(this::addRenderableWidget);
    repositionElements();
    setInitialFocus(filter);
  }

  @Override
  protected void repositionElements() {
    if (this.footerWidth != this.width) {
      rebuildWidgets();
      return;
    }
    this.layout.arrangeElements();
  }
}
