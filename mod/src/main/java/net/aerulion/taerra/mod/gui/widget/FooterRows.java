package net.aerulion.taerra.mod.gui.widget;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FooterRows {

  private static final int GAP = 8;
  private static final int ROW = 24;
  private static final int MARGIN = 16;

  private FooterRows() {}

  public static int add(
      final @NotNull HeaderAndFooterLayout layout,
      final int width,
      final int height,
      final @NotNull List<? extends AbstractWidget> buttons) {
    final int room = width - MARGIN;
    final @NotNull LinearLayout rows = layout.addToFooter(LinearLayout.vertical().spacing(4));
    rows.defaultCellSetting().alignHorizontallyCenter();
    @Nullable LinearLayout row = null;
    int used = 0;
    int count = 0;
    for (final @NotNull AbstractWidget button : buttons) {
      button.setWidth(Math.min(button.getWidth(), room));
      if (row == null || used + GAP + button.getWidth() > room) {
        row = rows.addChild(LinearLayout.horizontal().spacing(GAP));
        used = button.getWidth();
        count++;
      } else {
        used += GAP + button.getWidth();
      }
      row.addChild(button);
    }
    layout.setFooterHeight(height + ROW * Math.max(0, count - 1));
    return count;
  }
}
