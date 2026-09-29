package net.aerulion.taerra.mod.gui.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldPicker {

  private static final int SEARCH_FROM = 8;
  private static final int SEARCH_HEIGHT = 24;
  private static final int MIN_WIDTH = 120;
  private static final int MAX_WIDTH = 200;
  private static final int MARGIN = 4;

  private final @NotNull WorldList list;
  private final Supplier<List<World>> worlds;
  private final Supplier<@Nullable String> current;
  private @Nullable EditBox search;
  private boolean open;
  private @NotNull String query = "";
  private int x;
  private int y;
  private int width;
  private int height;

  public WorldPicker(
      final Supplier<List<World>> worlds,
      final Supplier<@Nullable String> current,
      final Consumer<World> onPick) {
    this.list = new WorldList(onPick);
    this.list.visible = false;
    this.worlds = worlds;
    this.current = current;
  }

  public @Nullable EditBox createSearch(final @NotNull Font font, final int worlds) {
    this.search = null;
    if (worlds < SEARCH_FROM) {
      return null;
    }
    final @NotNull Component hint = Component.translatable("taerra.map.world.search");
    final @NotNull EditBox search = new EditBox(font, 0, 20, hint);
    search.setHint(hint);
    search.setValue(this.query);
    search.setResponder(
        value -> {
          this.query = value;
          refresh();
        });
    search.visible = this.open;
    this.search = search;
    return search;
  }

  public boolean isOpen() {
    return this.open;
  }

  public void setOpen(final boolean open) {
    this.open = open;
    this.query = "";
    this.list.visible = open;
    if (this.search != null) {
      this.search.setValue("");
      this.search.visible = open;
    }
    refresh();
  }

  public void refresh() {
    if (this.open) {
      this.list.refresh(this.worlds.get(), this.query, this.current.get());
    }
  }

  public @Nullable EditBox search() {
    return this.search;
  }

  public @NotNull WorldList list() {
    return this.list;
  }

  public @Nullable World first() {
    return this.list.first();
  }

  public void layout(
      final @NotNull Font font,
      final int anchorX,
      final int screenWidth,
      final int top,
      final int bottom) {
    final int searchHeight = this.search == null ? 0 : SEARCH_HEIGHT;
    this.width = Math.clamp(this.list.preferredWidth(font), MIN_WIDTH, MAX_WIDTH);
    final int listBottom = bottom - MARGIN;
    final int listHeight =
        Math.max(
            0, Math.min(this.list.preferredHeight(), listBottom - top - 2 * MARGIN - searchHeight));
    this.x = Math.clamp(anchorX, MARGIN, screenWidth - this.width - MARGIN);
    final int listTop = listBottom - listHeight;
    this.list.updateSizeAndPosition(this.width, listHeight, this.x, listTop);
    if (this.search != null) {
      this.search.setPosition(this.x + 2, listTop - searchHeight + 2);
      this.search.setWidth(this.width - 4);
    }
    this.y = listTop - searchHeight;
    this.height = listHeight + searchHeight;
  }

  public boolean contains(final double mouseX, final double mouseY) {
    return this.open
        && mouseX >= this.x
        && mouseX < this.x + this.width
        && mouseY >= this.y
        && mouseY < this.y + this.height;
  }

  public void render(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    TooltipRenderUtil.extractTooltipBackground(
        graphics, this.x, this.y, this.width, this.height, null);
    this.list.extractRenderState(graphics, mouseX, mouseY, partialTick);
  }
}
