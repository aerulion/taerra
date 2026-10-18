package net.aerulion.taerra.mod.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public final class DiscoveryToast implements Toast {

  private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
  private static final double DISPLAY_MILLIS = 5000.0;
  private static final int TITLE_COLOR = 0xFFFFFF00;
  private static final int TEXT_WIDTH = 125;

  private final Component region;
  private final ItemStack icon = new ItemStack(Items.FILLED_MAP);
  private Visibility visibility = Visibility.HIDE;

  private DiscoveryToast(final @NotNull String region) {
    this.region = Component.literal(region);
  }

  public static void show(final @NotNull String region) {
    Minecraft.getInstance().gui.toastManager().addToast(new DiscoveryToast(region));
  }

  @Override
  public @NotNull Visibility getWantedVisibility() {
    return this.visibility;
  }

  @Override
  public void update(final @NotNull ToastManager manager, final long visibleTime) {
    this.visibility =
        visibleTime >= DISPLAY_MILLIS * manager.getNotificationDisplayTimeMultiplier()
            ? Visibility.HIDE
            : Visibility.SHOW;
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics, final @NotNull Font font, final long time) {
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
    graphics.text(font, Component.translatable("taerra.discovered"), 30, 7, TITLE_COLOR, false);
    graphics.text(font, font.split(this.region, TEXT_WIDTH).getFirst(), 30, 18, -1, false);
    graphics.fakeItem(this.icon, 8, 8);
  }
}
