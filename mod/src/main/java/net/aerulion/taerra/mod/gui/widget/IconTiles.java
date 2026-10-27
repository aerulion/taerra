package net.aerulion.taerra.mod.gui.widget;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.MapIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IconTiles extends AbstractWidget {

  public static final int TILE = 24;
  private static final int ICON = 16;

  private final @NotNull WebMap map;
  private final @NotNull String chosen;
  private final @NotNull Supplier<@Nullable Identifier> fallback;
  private final @NotNull Component fallbackLabel;
  private final @NotNull Consumer<String> onPick;
  private int scroll;
  private @NotNull String query = "";

  public IconTiles(
      final int columns,
      final int rows,
      final @NotNull WebMap map,
      final @NotNull String chosen,
      final @NotNull Supplier<@Nullable Identifier> fallback,
      final @NotNull Component fallbackLabel,
      final @NotNull Consumer<String> onPick) {
    super(0, 0, columns * TILE, rows * TILE, Component.translatable("taerra.icons.choose"));
    this.map = map;
    this.chosen = chosen;
    this.fallback = fallback;
    this.fallbackLabel = fallbackLabel;
    this.onPick = onPick;
  }

  private static @NotNull String fold(final @NotNull String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  public void filter(final @NotNull String query) {
    this.query = fold(query.strip());
    this.scroll = 0;
  }

  private int columns() {
    return getWidth() / TILE;
  }

  private int rows() {
    return getHeight() / TILE;
  }

  private @NotNull List<String> ids() {
    final @NotNull List<String> ids = new ArrayList<>();
    if (this.query.isEmpty()) {
      ids.add("");
    }
    this.map.icons().stream()
        .filter(icon -> fold(icon.displayName().getString()).contains(this.query))
        .forEach(icon -> ids.add(icon.id()));
    return ids;
  }

  private int maxScroll() {
    return Math.max(0, (ids().size() + columns() - 1) / columns() - rows());
  }

  @Override
  protected void extractWidgetRenderState(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    this.scroll = Math.clamp(this.scroll, 0, maxScroll());
    final @NotNull List<String> ids = ids();
    final int first = this.scroll * columns();
    for (int i = first; i < Math.min(ids.size(), first + columns() * rows()); i++) {
      final int x = getX() + (i - first) % columns() * TILE;
      final int y = getY() + (i - first) / columns() * TILE;
      final @NotNull String id = ids.get(i);
      final boolean hovered = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
      final boolean picked = id.equals(this.chosen);
      graphics.fill(
          x + 1,
          y + 1,
          x + TILE - 1,
          y + TILE - 1,
          picked ? CommonColors.WHITE : hovered ? CommonColors.LIGHT_GRAY : CommonColors.BLACK);
      graphics.fill(x + 2, y + 2, x + TILE - 2, y + TILE - 2, picked ? 0xFF48494A : 0xFF1E1E1F);
      final @Nullable Identifier texture =
          id.isEmpty() ? this.fallback.get() : MapIcons.icon(this.map, id);
      if (texture != null) {
        final int at = (TILE - ICON) / 2;
        graphics.blit(texture, x + at, y + at, x + at + ICON, y + at + ICON, 0, 1, 0, 1);
      }
      if (hovered) {
        final @Nullable MapIcon icon = this.map.icon(id);
        graphics.setTooltipForNextFrame(
            icon == null ? this.fallbackLabel : icon.displayName(), mouseX, mouseY);
      }
    }
    if (maxScroll() > 0) {
      final int track = getHeight();
      final int thumb = Math.max(8, track * rows() / (rows() + maxScroll()));
      final int top = getY() + (track - thumb) * this.scroll / maxScroll();
      graphics.fill(getRight() + 2, getY(), getRight() + 4, getBottom(), 0x40FFFFFF);
      graphics.fill(getRight() + 2, top, getRight() + 4, top + thumb, 0xFFC0C0C0);
    }
  }

  @Override
  public void onClick(final @NotNull MouseButtonEvent event, final boolean doubleClick) {
    final int column = (int) (event.x() - getX()) / TILE;
    final int row = (int) (event.y() - getY()) / TILE;
    final int index = (this.scroll + row) * columns() + column;
    final @NotNull List<String> ids = ids();
    if (column >= 0 && column < columns() && row >= 0 && index < ids.size()) {
      playButtonClickSound(Minecraft.getInstance().getSoundManager());
      this.onPick.accept(ids.get(index));
    }
  }

  @Override
  public void playDownSound(final @NotNull SoundManager sounds) {}

  @Override
  public boolean mouseScrolled(
      final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
    if (!isMouseOver(mouseX, mouseY) || maxScroll() == 0) {
      return false;
    }
    this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll());
    return true;
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
