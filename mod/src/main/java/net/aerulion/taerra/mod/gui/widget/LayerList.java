package net.aerulion.taerra.mod.gui.widget;

import java.util.List;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LayerList extends ContainerObjectSelectionList<LayerList.Entry> {

  private static final int ROW = 20;
  private static final int COUNT_GAP = 4;
  private static final String ELLIPSIS = "…";

  private final WebMap map;

  public LayerList(final WebMap map) {
    super(Minecraft.getInstance(), PanelList.WIDTH, 0, 0, ROW);
    this.map = map;
  }

  public void refresh(final @Nullable String world) {
    final List<Layer> layers = world == null ? List.of() : this.map.markers(world);
    replaceEntries(layers.stream().map(Entry::new).toList());
  }

  @Override
  protected int scrollBarX() {
    return getRight() - 6;
  }

  @Override
  public int getRowWidth() {
    return this.width - 12;
  }

  public final class Entry extends ContainerObjectSelectionList.Entry<Entry> {

    private final @NotNull String count;
    private final @NotNull Checkbox checkbox;

    private Entry(final @NotNull Layer layer) {
      final @NotNull Font font = Minecraft.getInstance().font;
      this.count = String.valueOf(layer.markers().size());
      final @NotNull String name = layer.displayName().getString();
      final int room =
          getRowWidth() - Checkbox.getBoxSize(font) - 4 - font.width(this.count) - COUNT_GAP;
      final boolean cut = font.width(name) > room;
      final @NotNull String label =
          cut ? font.plainSubstrByWidth(name, room - font.width(ELLIPSIS)) + ELLIPSIS : name;
      final Checkbox.@NotNull Builder checkbox =
          Checkbox.builder(Component.literal(label), font)
              .selected(LayerList.this.map.shown(layer))
              .onValueChange((_, shown) -> LayerList.this.map.setShown(layer, shown));
      if (layer.restricted()) {
        checkbox.tooltip(Tooltip.create(Component.translatable("taerra.private")));
      } else if (cut) {
        checkbox.tooltip(Tooltip.create(Component.literal(name)));
      }
      this.checkbox = checkbox.build();
    }

    @Override
    public void extractContent(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      final @NotNull Font font = Minecraft.getInstance().font;
      this.checkbox.setPosition(getContentX(), getContentYMiddle() - this.checkbox.getHeight() / 2);
      this.checkbox.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.text(
          font,
          this.count,
          getContentRight() - font.width(this.count),
          getContentYMiddle() - 4,
          0xFFA0A0A0,
          true);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return List.of(this.checkbox);
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return List.of(this.checkbox);
    }
  }
}
