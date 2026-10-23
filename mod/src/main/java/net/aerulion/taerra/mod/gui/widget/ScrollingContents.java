package net.aerulion.taerra.mod.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.jetbrains.annotations.NotNull;

public final class ScrollingContents {

  private final @NotNull LinearLayout contents;
  private final @NotNull ScrollableLayout scroll;

  public ScrollingContents(
      final @NotNull Minecraft minecraft,
      final @NotNull HeaderAndFooterLayout layout,
      final @NotNull LinearLayout contents) {
    this.contents = contents;
    this.scroll = layout.addToContents(new ScrollableLayout(minecraft, contents, 0));
  }

  public void fit(final @NotNull HeaderAndFooterLayout layout) {
    this.contents.arrangeElements();
    final int height =
        Math.clamp(this.contents.getHeight(), 0, Math.max(0, layout.getContentHeight()));
    this.scroll.setMinHeight(height);
    this.scroll.setMaxHeight(height);
  }
}
