package net.aerulion.taerra.mod.gui.widget;

import java.util.function.Consumer;
import net.aerulion.taerra.mod.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class ColorPicker extends AbstractWidget {

  private static final int SHADE_HEIGHT = 28;
  private static final int GAP = 3;
  private static final int HUE_HEIGHT = 6;
  private static final double STEP = .02;
  private static final double MAX_HUE = 359;

  private final Consumer<String> onChange;
  private double hue;
  private double saturation;
  private double value;
  private boolean draggingHue;

  public ColorPicker(final int width, final @NotNull String hex, final Consumer<String> onChange) {
    super(
        0,
        0,
        width,
        SHADE_HEIGHT + GAP + HUE_HEIGHT,
        Component.translatable("taerra.waypoint.shade"));
    this.onChange = onChange;
    setHex(hex);
  }

  public void setHex(final @NotNull String hex) {
    final double @NotNull [] hsv = Colors.toHsv(Colors.argb(hex));
    if (hsv[1] != 0) {
      this.hue = hsv[0];
    }
    this.saturation = hsv[1];
    this.value = hsv[2];
  }

  @Override
  protected void extractWidgetRenderState(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    final int x = getX();
    final int y = getY();
    final int width = getWidth();
    final int span = Math.max(1, width - 1);
    for (int i = 0; i < width; i++) {
      graphics.fillGradient(
          x + i,
          y,
          x + i + 1,
          y + SHADE_HEIGHT,
          0xFF000000 | Colors.fromHsv(this.hue, (double) i / span, 1),
          0xFF000000);
    }
    final int thumbX = x + (int) Math.round(this.saturation * span);
    final int thumbY = y + (int) Math.round((1 - this.value) * (SHADE_HEIGHT - 1));
    graphics.fill(thumbX - 2, thumbY - 2, thumbX + 3, thumbY + 3, 0xFF000000);
    graphics.fill(thumbX - 1, thumbY - 1, thumbX + 2, thumbY + 2, 0xFFFFFFFF);
    graphics.fill(
        thumbX,
        thumbY,
        thumbX + 1,
        thumbY + 1,
        0xFF000000 | Colors.fromHsv(this.hue, this.saturation, this.value));

    final int hueY = y + SHADE_HEIGHT + GAP;
    for (int i = 0; i < width; i++) {
      graphics.fill(
          x + i,
          hueY,
          x + i + 1,
          hueY + HUE_HEIGHT,
          0xFF000000 | Colors.fromHsv(i * 360.0 / width, 1, 1));
    }
    final int hueX = x + (int) Math.round(this.hue / MAX_HUE * span);
    graphics.fill(hueX - 1, hueY - 1, hueX + 2, hueY + HUE_HEIGHT + 1, 0xFF000000);
    graphics.fill(hueX, hueY - 1, hueX + 1, hueY + HUE_HEIGHT + 1, 0xFFFFFFFF);
    if (isFocused()) {
      graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFFFFFFFF);
      graphics.fill(x - 1, y + SHADE_HEIGHT, x + width + 1, y + SHADE_HEIGHT + 1, 0xFFFFFFFF);
      graphics.fill(x - 1, y, x, y + SHADE_HEIGHT, 0xFFFFFFFF);
      graphics.fill(x + width, y, x + width + 1, y + SHADE_HEIGHT, 0xFFFFFFFF);
    }
  }

  @Override
  public void onClick(final @NotNull MouseButtonEvent event, final boolean doubleClick) {
    this.draggingHue = event.y() >= getY() + SHADE_HEIGHT + GAP / 2.0;
    pick(event.x(), event.y());
  }

  @Override
  protected void onDrag(
      final @NotNull MouseButtonEvent event, final double dragX, final double dragY) {
    pick(event.x(), event.y());
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }

  @Override
  public boolean keyPressed(final @NotNull KeyEvent event) {
    final double step = STEP * (event.hasShiftDown() ? 5 : 1);
    if (event.isLeft() || event.isRight()) {
      this.saturation = Math.clamp(this.saturation + (event.isRight() ? step : -step), 0, 1);
    } else if (event.isUp() || event.isDown()) {
      this.value = Math.clamp(this.value + (event.isUp() ? step : -step), 0, 1);
    } else {
      return false;
    }
    changed();
    return true;
  }

  private void pick(final double mouseX, final double mouseY) {
    final double across = Math.clamp((mouseX - getX()) / Math.max(1, getWidth() - 1), 0, 1);
    if (this.draggingHue) {
      this.hue = across * MAX_HUE;
    } else {
      this.saturation = across;
      this.value = Math.clamp(1 - (mouseY - getY()) / (SHADE_HEIGHT - 1), 0, 1);
    }
    changed();
  }

  private void changed() {
    this.onChange.accept(Colors.hex(Colors.fromHsv(this.hue, this.saturation, this.value)));
  }
}
