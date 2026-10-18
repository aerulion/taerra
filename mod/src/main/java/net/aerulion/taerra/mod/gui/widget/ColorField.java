package net.aerulion.taerra.mod.gui.widget;

import java.util.Locale;
import java.util.function.Consumer;
import net.aerulion.taerra.mod.util.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ColorField {

  private final int width;
  private final @NotNull ColorPicker picker;
  private final @NotNull Consumer<String> changed;
  private @NotNull String color;
  private @Nullable EditBox hex;

  public ColorField(
      final int width, final @NotNull String color, final @NotNull Consumer<String> changed) {
    this.width = width;
    this.color = color;
    this.changed = changed;
    this.picker = new ColorPicker(width, color, this::show);
  }

  public @NotNull String color() {
    return this.color;
  }

  public void addTo(final @NotNull LinearLayout contents, final @NotNull Font font) {
    final @NotNull LinearLayout row = contents.addChild(LinearLayout.horizontal().spacing(4));
    row.addChild(new DyeSwatches(() -> this.color, this::pick));
    final @NotNull EditBox hex =
        row.addChild(
            new EditBox(
                font,
                this.width - 4 - DyeColor.values().length * DyeSwatches.SWATCH / 2,
                20,
                Component.translatable("taerra.waypoint.hex")));
    hex.setMaxLength(7);
    hex.setValue(this.color);
    hex.setTextColor(Colors.argb(this.color));
    hex.setTooltip(Tooltip.create(Component.translatable("taerra.waypoint.hex")));
    hex.setResponder(
        value -> {
          final @NotNull String digits = value.startsWith("#") ? value.substring(1) : value;
          final @NotNull String typed = "#" + digits.toLowerCase(Locale.ROOT);
          if (Colors.isHex(typed) && !typed.equals(this.color)) {
            this.color = typed;
            this.picker.setHex(typed);
            hex.setTextColor(Colors.argb(typed));
            this.changed.accept(typed);
          }
        });
    this.hex = hex;
    contents.addChild(this.picker);
  }

  private void pick(final @NotNull String color) {
    this.picker.setHex(color);
    show(color);
  }

  private void show(final @NotNull String color) {
    this.color = color;
    if (this.hex != null) {
      this.hex.setValue(color);
      this.hex.setTextColor(Colors.argb(color));
    }
    this.changed.accept(color);
  }
}
