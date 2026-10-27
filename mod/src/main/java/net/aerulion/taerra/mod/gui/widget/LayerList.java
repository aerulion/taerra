package net.aerulion.taerra.mod.gui.widget;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.minecraft.ChatFormatting;
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
  private final DoubleSupplier scale;

  public LayerList(final WebMap map, final DoubleSupplier scale) {
    super(Minecraft.getInstance(), PanelList.WIDTH, 0, 0, ROW);
    this.map = map;
    this.scale = scale;
  }

  public void refresh(final @Nullable String world) {
    final List<Layer> layers = world == null ? List.of() : this.map.markers(world);
    replaceEntries(
        Stream.concat(Stream.of(Layer.PLAYERS), layers.stream()).map(Entry::new).toList());
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

    private final @NotNull Layer layer;
    private final @NotNull String count;
    private final @NotNull Checkbox checkbox;
    private final @Nullable Tooltip tooltip;
    private boolean reached = true;

    private Entry(final @NotNull Layer layer) {
      this.layer = layer;
      final @NotNull Font font = Minecraft.getInstance().font;
      this.count = layer == Layer.PLAYERS ? "" : String.valueOf(layer.markers().size());
      final @NotNull String name = layer.displayName().getString();
      final int room =
          getRowWidth()
              - Checkbox.getBoxSize(font)
              - 4
              - font.width(this.count)
              - COUNT_GAP
              - (layer.personal() ? font.width(" " + MapIcons.PERSONAL) : 0);
      final boolean cut = font.width(name) > room;
      final @NotNull String label =
          cut ? font.plainSubstrByWidth(name, room - font.width(ELLIPSIS)) + ELLIPSIS : name;
      final Checkbox.@NotNull Builder checkbox =
          Checkbox.builder(
                  layer.personal()
                      ? Component.literal(label)
                          .append(
                              Component.literal(" " + MapIcons.PERSONAL)
                                  .withStyle(ChatFormatting.GOLD))
                      : Component.literal(label),
                  font)
              .selected(LayerList.this.map.shown(layer))
              .onValueChange((_, shown) -> LayerList.this.map.setShown(layer, shown));
      if (layer.restricted()) {
        this.tooltip = Tooltip.create(Component.translatable("taerra.private"));
      } else if (layer.personal()) {
        this.tooltip = Tooltip.create(Component.translatable("taerra.personal"));
      } else if (cut) {
        this.tooltip = Tooltip.create(Component.literal(name));
      } else {
        this.tooltip = null;
      }
      this.checkbox = checkbox.build();
      this.checkbox.setTooltip(this.tooltip);
    }

    @Override
    public void extractContent(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      final @NotNull Font font = Minecraft.getInstance().font;
      final boolean reached = this.layer.reaches(LayerList.this.scale.getAsDouble());
      if (reached != this.reached) {
        this.reached = reached;
        this.checkbox.setAlpha(reached ? 1 : .5F);
        this.checkbox.setTooltip(
            reached
                ? this.tooltip
                : Tooltip.create(Component.translatable("taerra.layers.zoom_in")));
      }
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
