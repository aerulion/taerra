package net.aerulion.taerra.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class Toasts {

  private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId();

  private Toasts() {}

  public static void show(final @NotNull Component title, final Component detail) {
    SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), ID, title, detail);
  }
}
