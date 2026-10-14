package net.aerulion.taerra.mod.gui.widget;

import java.util.Map;
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
  private static final Map<Character, Integer> PALETTE =
      Map.ofEntries(
          Map.entry('s', 0xFFFFCF3F),
          Map.entry('r', 0xFFE0A82E),
          Map.entry('o', 0xFFFF8A3D),
          Map.entry('p', 0xFFFFB15C),
          Map.entry('h', 0xFFB35A1F),
          Map.entry('w', 0xFF8A4518),
          Map.entry('m', 0xFFE3E4E7),
          Map.entry('d', 0xFF8C8D90),
          Map.entry('c', 0xFFA0A1A4),
          Map.entry('u', 0xFF6E6F72),
          Map.entry('b', 0xFF5B9BD5),
          Map.entry('t', 0xFF7D7E82),
          Map.entry('l', 0xFF58585A));
  private static final String[] SUN = {
    "....r....",
    ".r.....r.",
    "...sss...",
    "..sssss..",
    "r.sssss.r",
    "..sssss..",
    "...sss...",
    ".r.....r.",
    "....r...."
  };
  private static final String[] SUNSET = {
    ".........",
    "....p....",
    ".p.....p.",
    "...ooo...",
    "p.ooooo.p",
    "..ooooo..",
    "hhhhhhhhh",
    "..wwwww..",
    "........."
  };
  private static final String[] MOON = {
    "...mmd...",
    "..mmd....",
    ".mmd.....",
    ".mmd.....",
    ".mmd.....",
    ".mmmd....",
    ".mmmmd...",
    "..mmmmmd.",
    "...mmmm.."
  };
  private static final String[] RAIN = {
    ".........",
    "...ccc...",
    ".ccccccc.",
    "ccccccccc",
    ".uuuuuuu.",
    ".........",
    ".b..b..b.",
    "b..b..b..",
    "........."
  };
  private static final String[] THUNDER = {
    "...ttt...",
    ".ttttttt.",
    "ttttttttt",
    ".lllllll.",
    "....ss...",
    "...ss....",
    "..ssss...",
    "...ss....",
    "...s....."
  };

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
    final String @NotNull [] glyph = glyph(sky);
    draw(graphics, glyph, left + 1, top + 1, true);
    draw(graphics, glyph, left, top, false);
  }

  private static String @NotNull [] glyph(final @NotNull Sky sky) {
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

  private static void draw(
      final @NotNull GuiGraphicsExtractor graphics,
      final String @NotNull [] glyph,
      final int left,
      final int top,
      final boolean shadow) {
    for (int row = 0; row < glyph.length; row++) {
      for (int column = 0; column < glyph[row].length(); column++) {
        final @Nullable Integer color = PALETTE.get(glyph[row].charAt(column));
        if (color != null) {
          graphics.fill(
              left + column, top + row, left + column + 1, top + row + 1, shadow ? SHADOW : color);
        }
      }
    }
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
