package net.aerulion.taerra.mod.gui.widget;

import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

public final class CompassButton extends AbstractButton {

  public static final int SIZE = 21;
  private static final int NEEDLE_WIDTH = 7;
  private static final int NEEDLE_HEIGHT = 15;
  private static final int NEEDLE_SHADOW = 0x80000000;
  private static final int[][] NEEDLE = {
    {0xFFE34A4A, 3, 0, 1, 2},
    {0xFFE34A4A, 2, 2, 2, 2},
    {0xFFE34A4A, 1, 4, 3, 3},
    {0xFFA32C2C, 4, 2, 1, 2},
    {0xFFA32C2C, 4, 4, 2, 3},
    {0xFFD0D1D4, 1, 7, 3, 4},
    {0xFFD0D1D4, 2, 11, 2, 2},
    {0xFFD0D1D4, 3, 13, 1, 2},
    {0xFF8C8D90, 4, 7, 2, 4},
    {0xFF8C8D90, 4, 11, 1, 2},
    {0xFF1E1E1F, 3, 7, 1, 1}
  };

  private final DoubleSupplier north;
  private final Runnable onPress;

  public CompassButton(final DoubleSupplier north, final Runnable onPress) {
    super(0, 0, SIZE, SIZE, Component.translatable("taerra.map.compass"));
    this.north = north;
    this.onPress = onPress;
  }

  @Override
  public void onPress(final InputWithModifiers input) {
    this.onPress.run();
  }

  @Override
  protected void extractContents(
      final GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    extractDefaultSprite(graphics);
    final double angle = this.north.getAsDouble();
    needle(graphics, angle, true);
    needle(graphics, angle, false);
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }

  private void needle(
      final @NotNull GuiGraphicsExtractor graphics, final double angle, final boolean shadow) {
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(getX() + SIZE / 2f, getY() + SIZE / 2f);
    pose.rotate((float) angle);
    pose.translate(-NEEDLE_WIDTH / 2f, -NEEDLE_HEIGHT / 2f);
    if (shadow) {
      pose.translate(.5f, .5f);
    }
    for (final int[] pixels : NEEDLE) {
      graphics.fill(
          pixels[1],
          pixels[2],
          pixels[1] + pixels[3],
          pixels[2] + pixels[4],
          shadow ? NEEDLE_SHADOW : pixels[0]);
    }
    pose.popMatrix();
  }
}
