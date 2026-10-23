package net.aerulion.taerra.mod.gui.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.session.MapSession;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.Distances;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.Marker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointLabels implements LevelRenderEvents.CollectSubmits {

  private static final float PIXEL = 0.025f;
  private static final double CLOSE = 2;
  private static final double FADE = 4;
  private static final int MIN_ALPHA = 4;
  private static final double STEADY = 10;
  private static final float ICON = 1;

  private final MapSession session;
  private final Settings settings;

  public WaypointLabels(final MapSession session, final Settings settings) {
    this.session = session;
    this.settings = settings;
  }

  private static void submit(
      final @NotNull LevelRenderContext context,
      final @NotNull PoseStack pose,
      final @NotNull Font font,
      final @NotNull Component component,
      final float y,
      final int color) {
    final @NotNull FormattedCharSequence text = component.getVisualOrderText();
    context
        .submitNodeCollector()
        .submitText(
            pose,
            -font.width(text) / 2f,
            y,
            text,
            true,
            Font.DisplayMode.NORMAL,
            LightCoordsUtil.FULL_BRIGHT,
            color,
            0,
            0);
  }

  private static void icon(
      final @NotNull LevelRenderContext context,
      final @NotNull PoseStack pose,
      final MapIcons.@NotNull Sprite sprite,
      final float tip,
      final int color) {
    final float left = -sprite.width() * ICON / 2;
    final float top = tip - sprite.tip() * ICON;
    final float right = -left;
    final float bottom = top + sprite.height() * ICON;
    context
        .submitNodeCollector()
        .submitCustomGeometry(
            pose,
            RenderTypes.text(sprite.texture()),
            (at, consumer) -> {
              vertex(consumer, at, left, top, 0, 0, color);
              vertex(consumer, at, left, bottom, 0, 1, color);
              vertex(consumer, at, right, bottom, 1, 1, color);
              vertex(consumer, at, right, top, 1, 0, color);
            });
  }

  private static void vertex(
      final @NotNull VertexConsumer consumer,
      final PoseStack.@NotNull Pose at,
      final float x,
      final float y,
      final float u,
      final float v,
      final int color) {
    consumer
        .addVertex(at, x, y, 0)
        .setColor(color)
        .setUv(u, v)
        .setLight(LightCoordsUtil.FULL_BRIGHT);
  }

  private static @NotNull Component text(final @NotNull Marker marker, final double distance) {
    return Component.literal((marker.label() == null ? marker.id() : marker.label()) + " ")
        .append(Component.literal(Distances.format(distance)).withStyle(ChatFormatting.GRAY));
  }

  @Override
  public void collectSubmits(final LevelRenderContext context) {
    final @Nullable WebMap map = this.session.map();
    if (!this.settings.waypointLabels() || map == null) {
      return;
    }
    final @NotNull CameraRenderState camera = context.levelState().cameraRenderState;
    final double reach = camera.fogData.renderDistanceEnd;
    final @NotNull Font font = Minecraft.getInstance().font;
    final @NotNull PoseStack pose = context.poseStack();
    for (final @NotNull Layer layer : map.markers(Dimensions.current())) {
      if (!layer.waypoints() || !map.shown(layer)) {
        continue;
      }
      for (final @NotNull Marker marker : layer.markers()) {
        final @Nullable List<Double> at = marker.position();
        if (!marker.type().equals("point") || at == null || at.size() < 3) {
          continue;
        }
        final @NotNull Vec3 offset =
            new Vec3(
                    Math.floor(at.get(0)) + .5,
                    Math.floor(at.get(1)) + 1,
                    Math.floor(at.get(2)) + .5)
                .subtract(camera.pos);
        final double distance = offset.length();
        final int alpha = (int) (255 * Math.clamp((distance - CLOSE) / FADE, 0, 1));
        if (alpha < MIN_ALPHA || distance > reach) {
          continue;
        }
        final int color = alpha << 24 | 0xFFFFFF;
        final float scale = (float) (PIXEL * Math.max(distance, STEADY) / STEADY);
        pose.pushPose();
        pose.translate(offset);
        pose.rotate(camera.orientation);
        pose.scale(scale, -scale, scale);
        final MapIcons.@NotNull Sprite sprite = MapIcons.sprite(map, layer, marker);
        icon(context, pose, sprite, 0, color);
        submit(
            context,
            pose,
            font,
            text(marker, distance),
            -sprite.tip() * ICON - 2 - font.lineHeight,
            color);
        pose.popPose();
      }
    }
  }
}
