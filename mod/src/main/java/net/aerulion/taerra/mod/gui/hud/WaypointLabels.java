package net.aerulion.taerra.mod.gui.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.session.MapSession;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.Marker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointLabels implements LevelRenderEvents.CollectSubmits {

  private static final float PIXEL = 0.025f;
  private static final double CLOSE = 2;
  private static final double STEADY = 10;
  private static final float ICON = 2;

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
      final boolean shadow) {
    final @NotNull FormattedCharSequence text = component.getVisualOrderText();
    context
        .submitNodeCollector()
        .submitText(
            pose,
            -font.width(text) / 2f,
            y,
            text,
            shadow,
            Font.DisplayMode.NORMAL,
            LightCoordsUtil.FULL_BRIGHT,
            0xFFFFFFFF,
            0,
            0);
  }

  private static @NotNull Component icon(final @NotNull Marker marker) {
    return Component.object(
        new AtlasSprite(
            AtlasIds.MAP_DECORATIONS,
            Identifier.withDefaultNamespace(MapIcons.bannerName(Colors.argb(marker.color())))));
  }

  private static @NotNull Component text(final @NotNull Marker marker, final double distance) {
    return Component.literal((marker.label() == null ? marker.id() : marker.label()) + " ")
        .append(Component.literal(Math.round(distance) + "m").withStyle(ChatFormatting.GRAY));
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
        if (distance < CLOSE || distance > reach) {
          continue;
        }
        final float scale = (float) (PIXEL * Math.max(distance, STEADY) / STEADY);
        pose.pushPose();
        pose.translate(offset);
        pose.rotate(camera.orientation);
        pose.scale(scale, -scale, scale);
        submit(context, pose, font, text(marker, distance), -font.lineHeight, true);
        pose.scale(ICON, ICON, ICON);
        submit(
            context,
            pose,
            font,
            icon(marker),
            -(font.lineHeight + 2) / ICON - font.lineHeight,
            false);
        pose.popPose();
      }
    }
  }
}
