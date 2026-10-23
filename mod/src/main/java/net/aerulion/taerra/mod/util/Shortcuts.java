package net.aerulion.taerra.mod.util;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class Shortcuts {

  private Shortcuts() {}

  public static boolean isUndo(final @NotNull KeyEvent event) {
    return event.hasControlDownWithQuirk() && event.shortcutKey() == 'z' && !event.hasShiftDown();
  }

  public static boolean isRedo(final @NotNull KeyEvent event) {
    return event.hasControlDownWithQuirk()
        && (event.shortcutKey() == 'y' || event.shortcutKey() == 'z' && event.hasShiftDown());
  }

  public static @NotNull Tooltip undoTooltip() {
    return tooltip("taerra.selection.undo.tooltip");
  }

  public static @NotNull Tooltip redoTooltip() {
    return tooltip("taerra.selection.redo.tooltip");
  }

  private static @NotNull Tooltip tooltip(final @NotNull String key) {
    return Tooltip.create(
        Component.translatable(
            key,
            Component.translatable(
                InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
                    ? "taerra.key.command"
                    : "taerra.key.control")));
  }
}
