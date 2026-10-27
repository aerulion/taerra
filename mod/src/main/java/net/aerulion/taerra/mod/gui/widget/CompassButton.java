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
  private static final Glyph NEEDLE = Glyph.of("compass/needle", NEEDLE_WIDTH, NEEDLE_HEIGHT);

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
    drawNeedle(graphics, getX() + SIZE / 2f, getY() + SIZE / 2f, this.north.getAsDouble());
  }

  public static void drawNeedle(
      final @NotNull GuiGraphicsExtractor graphics,
      final float centerX,
      final float centerY,
      final double angle) {
    needle(graphics, centerX, centerY, angle, true);
    needle(graphics, centerX, centerY, angle, false);
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }

  private static void needle(
      final @NotNull GuiGraphicsExtractor graphics,
      final float centerX,
      final float centerY,
      final double angle,
      final boolean shadow) {
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(centerX, centerY);
    pose.rotate((float) angle);
    pose.translate(-NEEDLE_WIDTH / 2f, -NEEDLE_HEIGHT / 2f);
    if (shadow) {
      pose.translate(.5f, .5f);
    }
    NEEDLE.draw(graphics, 0, 0, shadow ? NEEDLE_SHADOW : 0xFFFFFFFF);
    pose.popMatrix();
  }
}
