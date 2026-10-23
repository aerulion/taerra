package net.aerulion.taerra.mod.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class PlaceMenu extends AbstractContainerEventHandler
    implements Renderable, NarratableEntry {

  private static final int ROW = 12;
  private static final int HEADER = 17;
  private static final int FRAME = 4;
  private static final int PAD = 2;
  private static final int GAP = 5;

  private final @NotNull Component title;
  private final List<Item> items = new ArrayList<>();
  private int x;
  private int y;
  private int width;
  private long closesAt;
  private boolean grouping;

  public PlaceMenu(final @NotNull Component title) {
    this.title = title.copy().withStyle(ChatFormatting.GRAY);
  }

  public @NotNull PlaceMenu add(final @NotNull Component label, final Consumer<Item> action) {
    this.items.add(new Item(label, action, this.grouping));
    this.grouping = false;
    return this;
  }

  public @NotNull PlaceMenu group() {
    this.grouping = !this.items.isEmpty();
    return this;
  }

  public boolean isEmpty() {
    return this.items.isEmpty();
  }

  public void place(
      final @NotNull Font font,
      final double mouseX,
      final double mouseY,
      final int left,
      final int top,
      final int right,
      final int bottom) {
    this.width = font.width(this.title);
    for (final @NotNull Item item : this.items) {
      this.width = Math.max(this.width, font.width(item.getMessage()));
    }
    this.x =
        (int)
            Math.clamp(
                mouseX + FRAME, left + FRAME, Math.max(left + FRAME, right - this.width - FRAME));
    this.y =
        (int)
            Math.clamp(
                mouseY + FRAME, top + FRAME, Math.max(top + FRAME, bottom - height() - FRAME));
    arrange();
  }

  public void closeIn(final long millis) {
    this.closesAt = System.currentTimeMillis() + millis;
  }

  public boolean expired() {
    return this.closesAt != 0 && System.currentTimeMillis() >= this.closesAt;
  }

  @Override
  public @NotNull List<? extends GuiEventListener> children() {
    return this.items;
  }

  @Override
  public boolean keyPressed(final @NotNull KeyEvent event) {
    if (event.isDown() || event.isUp()) {
      final int step = event.isDown() ? 1 : -1;
      final int current = this.items.indexOf(getFocused());
      final int next =
          current < 0
              ? (step > 0 ? 0 : this.items.size() - 1)
              : Math.floorMod(current + step, this.items.size());
      setFocused(this.items.get(next));
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public boolean isMouseOver(final double mouseX, final double mouseY) {
    return mouseX >= this.x - FRAME
        && mouseX < this.x + this.width + FRAME
        && mouseY >= this.y - FRAME
        && mouseY < this.y + height() + FRAME;
  }

  @Override
  public void extractRenderState(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    final @NotNull Font font = Minecraft.getInstance().font;
    for (final @NotNull Item item : this.items) {
      this.width = Math.max(this.width, font.width(item.getMessage()));
    }
    arrange();
    TooltipRenderUtil.extractTooltipBackground(
        graphics, this.x, this.y, this.width, height(), null);
    graphics.text(font, this.title, this.x, this.y + PAD, 0xFFFFFFFF, true);
    graphics.fill(
        this.x, this.y + HEADER - 4, this.x + this.width, this.y + HEADER - 3, 0x33FFFFFF);
    for (final @NotNull Item item : this.items) {
      if (item.opensGroup) {
        graphics.fill(this.x, item.getY() - 3, this.x + this.width, item.getY() - 2, 0x33FFFFFF);
      }
      item.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  @Override
  public @NotNull NarrationPriority narrationPriority() {
    return isFocused() ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
  }

  @Override
  public void updateNarration(final NarrationElementOutput output) {
    if (getFocused() instanceof final @NotNull Item item) {
      item.updateNarration(output);
    }
  }

  private int height() {
    int height = HEADER + this.items.size() * ROW - 2 * PAD;
    for (final @NotNull Item item : this.items) {
      height += item.opensGroup ? GAP : 0;
    }
    return height;
  }

  private void arrange() {
    int top = this.y + HEADER - PAD;
    for (final @NotNull Item item : this.items) {
      top += item.opensGroup ? GAP : 0;
      item.setRectangle(this.width + 2 * PAD, ROW, this.x - PAD, top);
      top += ROW;
    }
  }

  public static final class Item extends AbstractButton {

    private static final int HOVERED = 0xFFFFFF55;
    private static final int BUSY = 0xFF808080;
    private static final int HIGHLIGHT = 0x28FFFFFF;

    private final Consumer<Item> action;
    private final boolean opensGroup;

    private Item(
        final @NotNull Component label, final Consumer<Item> action, final boolean opensGroup) {
      super(0, 0, 0, ROW, label);
      this.action = action;
      this.opensGroup = opensGroup;
    }

    public void show(final @NotNull Component label, final boolean busy) {
      setMessage(label);
      this.active = !busy;
    }

    @Override
    public void onPress(final InputWithModifiers input) {
      this.action.accept(this);
    }

    @Override
    protected void extractContents(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick) {
      final boolean highlighted = this.active && isHoveredOrFocused();
      if (highlighted) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), HIGHLIGHT);
      }
      graphics.text(
          Minecraft.getInstance().font,
          getMessage(),
          getX() + PAD,
          getY() + PAD,
          !this.active ? BUSY : highlighted ? HOVERED : 0xFFFFFFFF,
          true);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }
  }
}
