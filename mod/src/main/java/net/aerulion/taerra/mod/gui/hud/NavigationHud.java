package net.aerulion.taerra.mod.gui.hud;

import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.session.MapSession;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.Distances;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.World;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

public final class NavigationHud {

  private static final int TOP = 4;
  private static final int HEIGHT = 14;
  private static final int ARROW = 8;
  private static final int GAP = 3;

  private final MapSession session;
  private final Navigation navigation;

  public NavigationHud(final MapSession session, final Navigation navigation) {
    this.session = session;
    this.navigation = navigation;
  }

  public @NotNull HudElement above(final @NotNull HudElement bossBars) {
    return (graphics, deltaTracker) -> {
      final boolean shown = draw(graphics, deltaTracker);
      graphics.pose().pushMatrix();
      graphics.pose().translate(0, shown ? HEIGHT : 0);
      bossBars.extractRenderState(graphics, deltaTracker);
      graphics.pose().popMatrix();
    };
  }

  private boolean draw(
      final @NotNull GuiGraphicsExtractor graphics, final @NotNull DeltaTracker deltaTracker) {
    final Navigation.@Nullable Target target = this.navigation.target();
    final @Nullable LocalPlayer self = Minecraft.getInstance().player;
    if (target == null || self == null) {
      return false;
    }
    final @NotNull Font font = Minecraft.getInstance().font;
    final @NotNull String here = Dimensions.current();
    final double @Nullable [] at = target.in(here);
    final @NotNull MutableComponent text = Component.literal(target.label());
    if (at == null) {
      final @Nullable WebMap map = this.session.map();
      final @Nullable World world = map == null ? null : map.world(target.world());
      text.append(
          Component.translatable(
                  "taerra.navigation.elsewhere",
                  world == null ? target.world() : world.displayName())
              .withStyle(ChatFormatting.GRAY));
    } else {
      final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
      final @NotNull Vec3 position = self.getPosition(partialTick);
      final double dx = at[0] - position.x;
      final double dz = at[2] - position.z;
      text.append(
          Component.literal("  " + Distances.format(Math.hypot(dx, dz)))
              .withStyle(ChatFormatting.GRAY));
      if (!target.world().equals(here)) {
        text.append(
            Component.literal(" ")
                .append(Component.translatable("taerra.navigation.portal"))
                .withStyle(ChatFormatting.DARK_GRAY));
      }
      final double turn =
          Math.toRadians(Math.toDegrees(Math.atan2(-dx, dz)) - self.getViewYRot(partialTick));
      final int left = (graphics.guiWidth() - ARROW - GAP - font.width(text)) / 2;
      final @NotNull Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.translate(left + ARROW / 2f, TOP + ARROW / 2f);
      pose.rotate((float) turn);
      pose.translate(-ARROW / 2f, -ARROW / 2f);
      graphics.blitSprite(
          RenderPipelines.GUI_TEXTURED,
          MapIcons.decoration("player"),
          0,
          0,
          ARROW,
          ARROW,
          target.color());
      pose.popMatrix();
      graphics.text(font, text, left + ARROW + GAP, TOP, 0xFFFFFFFF, true);
      return true;
    }
    graphics.text(font, text, (graphics.guiWidth() - font.width(text)) / 2, TOP, 0xFFFFFFFF, true);
    return true;
  }
}
