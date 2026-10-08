package net.aerulion.taerra.mod.gui.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.util.Dimensions;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NavigationBeam implements LevelRenderEvents.CollectSubmits {

  private static final float FOG_MARGIN = .8f;
  private static final float BEAM_SCALE_DISTANCE = 96;
  private static final int ANIMATION_TICKS = 40;

  private final Navigation navigation;

  public NavigationBeam(final Navigation navigation) {
    this.navigation = navigation;
  }

  @Override
  public void collectSubmits(final LevelRenderContext context) {
    final Navigation.@Nullable Target target = this.navigation.target();
    final @Nullable ClientLevel level = Minecraft.getInstance().level;
    final double @Nullable [] at = target == null ? null : target.in(Dimensions.current());
    if (at == null || level == null) {
      return;
    }
    final @NotNull CameraRenderState camera = context.levelState().cameraRenderState;
    final @NotNull Vec3 eye = camera.pos;
    double dx = at[0] - eye.x;
    double dz = at[2] - eye.z;
    final double distance = Math.hypot(dx, dz);
    final double reach = camera.fogData.renderDistanceStart * FOG_MARGIN;
    if (distance > reach && distance > 0) {
      dx *= reach / distance;
      dz *= reach / distance;
    }
    final double bottom = Double.isNaN(at[1]) ? level.getMinY() : Math.floor(at[1]);
    final float partialTick =
        Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    final @NotNull PoseStack pose = context.poseStack();
    pose.pushPose();
    pose.translate(dx - .5, bottom - eye.y, dz - .5);
    final float scale = (float) Math.max(1, Math.min(distance, reach) / BEAM_SCALE_DISTANCE);
    BeaconRenderer.submitBeaconBeam(
        pose,
        context.submitNodeCollector(),
        BeaconRenderer.BEAM_LOCATION,
        1,
        Math.floorMod(level.getGameTime(), ANIMATION_TICKS) + partialTick,
        0,
        BeaconRenderer.MAX_RENDER_Y,
        target.color(),
        BeaconRenderer.SOLID_BEAM_RADIUS * scale,
        BeaconRenderer.BEAM_GLOW_RADIUS * scale);
    pose.popPose();
  }
}
