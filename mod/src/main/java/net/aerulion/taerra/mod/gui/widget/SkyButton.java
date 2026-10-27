package net.aerulion.taerra.mod.gui.widget;

import java.util.function.Supplier;
import net.aerulion.taerra.mod.web.model.Sky;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SkyButton extends AbstractButton {

  public static final int GLYPH = 9;
  private static final int SHADOW = 0x80000000;
  private static final int BAR_WIDTH = 11;
  private static final int BAR_TRACK = 0xFF1E1E1F;
  private static final Glyph SUN = Glyph.of("sky/sun", GLYPH, GLYPH);
  private static final Glyph SUNSET = Glyph.of("sky/sunset", GLYPH, GLYPH);
  private static final Glyph MOON = Glyph.of("sky/moon", GLYPH, GLYPH);
  private static final Glyph RAIN = Glyph.of("sky/rain", GLYPH, GLYPH);
  private static final Glyph THUNDER = Glyph.of("sky/thunder", GLYPH, GLYPH);

  private final Supplier<@Nullable Sky> sky;
  private final Runnable onPress;
  private @Nullable Sky shown;

  public SkyButton(final Supplier<@Nullable Sky> sky, final Runnable onPress) {
    super(0, 0, CompassButton.SIZE, CompassButton.SIZE, Component.translatable("taerra.sky"));
    this.sky = sky;
    this.onPress = onPress;
  }

  public static void drawGlyph(
      final @NotNull GuiGraphicsExtractor graphics,
      final int left,
      final int top,
      final @NotNull Sky sky) {
    final @NotNull Glyph glyph = glyph(sky);
    glyph.draw(graphics, left + 1, top + 1, SHADOW);
    glyph.draw(graphics, left, top, 0xFFFFFFFF);
  }

  private static @NotNull Glyph glyph(final @NotNull Sky sky) {
    return switch (sky.weather()) {
      case "thunder" -> THUNDER;
      case "rain" -> RAIN;
      default ->
          switch (sky.phase()) {
            case "day" -> SUN;
            case "night" -> MOON;
            default -> SUNSET;
          };
    };
  }

  private static int barColor(final @NotNull Sky sky) {
    if (!sky.advancing()) {
      return 0xFF58585A;
    }
    return switch (sky.phase()) {
      case "day" -> 0xFFFFCF3F;
      case "night" -> 0xFF8FA6D9;
      default -> 0xFFD4732F;
    };
  }

  public void sync() {
    final @Nullable Sky sky = this.sky.get();
    this.visible = sky != null;
    if (sky != null && !sky.equals(this.shown)) {
      setTooltip(Tooltip.create(sky.describe()));
      setMessage(sky.describe());
    }
    this.shown = sky;
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
    final @Nullable Sky sky = this.shown;
    if (sky == null) {
      return;
    }
    final int left = getX() + (getWidth() - GLYPH) / 2;
    drawGlyph(graphics, left, getY() + 3, sky);
    final int barLeft = getX() + (getWidth() - BAR_WIDTH) / 2;
    final int barTop = getY() + 14;
    graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + 2, BAR_TRACK);
    graphics.fill(
        barLeft,
        barTop,
        barLeft + (int) Math.round(BAR_WIDTH * sky.progress()),
        barTop + 2,
        barColor(sky));
  }

  @Override
  protected void updateWidgetNarration(final NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
