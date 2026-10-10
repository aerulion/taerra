package net.aerulion.taerra.mod.gui.hud;

import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.gui.widget.CompassButton;
import net.aerulion.taerra.mod.map.MapView;
import net.aerulion.taerra.mod.map.MapViewImpl;
import net.aerulion.taerra.mod.map.Projection;
import net.aerulion.taerra.mod.session.MapSession;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.World;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Minimap implements HudElement {

  private static final Identifier FRAME =
      Identifier.withDefaultNamespace("container/inventory/effect_background");
  private static final int MARGIN = 4;
  private static final int INSET = 3;
  private static final int COORDINATES = 12;
  private static final int NEEDLE_INSET = 9;

  private final MapSession session;
  private final @NotNull Settings settings;
  private final @NotNull MapView view;

  public Minimap(final MapSession session, final @NotNull Settings settings) {
    this.session = session;
    this.settings = settings;
    this.view = new MapViewImpl(settings, settings.minimapZoom(), true);
  }

  public static int closestZoom(final @Nullable WebMap map) {
    final @Nullable MapConfig config = map == null ? null : map.config();
    if (config == null) {
      return Settings.MAX_ZOOM;
    }
    return Math.clamp(
        (long)
            Math.floor(
                Projection.closestZoom(
                    config, false, Minecraft.getInstance().getWindow().getGuiScale())),
        Settings.MIN_ZOOM,
        Settings.MAX_ZOOM);
  }

  public static int farthestZoom(final @Nullable WebMap map) {
    final @Nullable MapConfig config = map == null ? null : map.config();
    final @Nullable World world = map == null ? null : map.world(Dimensions.current());
    if (config == null || world == null) {
      return Settings.MIN_ZOOM;
    }
    return Math.clamp(world.minZoom(config, false, 0) - 1, Settings.MIN_ZOOM, closestZoom(map));
  }

  public @NotNull HudElement below(final @NotNull HudElement effects) {
    return (graphics, deltaTracker) -> {
      final int shift =
          shown() && !this.settings.minimapLeft()
              ? MARGIN + this.settings.minimapSize() + 2 * INSET + COORDINATES
              : 0;
      graphics.pose().pushMatrix();
      graphics.pose().translate(0, shift);
      effects.extractRenderState(graphics, deltaTracker);
      graphics.pose().popMatrix();
    };
  }

  private boolean shown() {
    final @NotNull Minecraft minecraft = Minecraft.getInstance();
    final @Nullable WebMap map = this.session.map();
    if (!this.settings.minimap()
        || map == null
        || minecraft.player == null
        || map.config() == null
        || minecraft.debugEntries.isOverlayVisible()) {
      return false;
    }
    this.view.setWorld(Dimensions.current());
    return map.world(this.view.world()) != null;
  }

  @Override
  public void extractRenderState(
      final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
    final @NotNull Minecraft minecraft = Minecraft.getInstance();
    final @Nullable WebMap map = this.session.map();
    final @Nullable LocalPlayer player = minecraft.player;
    if (map == null || player == null || !shown()) {
      return;
    }
    final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
    final @NotNull Vec3 position = player.getPosition(partialTick);
    this.view.centerOn(map.config(), position.x, position.y, position.z);
    this.view.zoomToward(this.settings.minimapZoom());
    this.view.setSpin(
        this.settings.minimapRotates()
            ? -Projection.topDown(map.config()).bearing(player.getViewYRot(partialTick))
            : 0);

    final int top = MARGIN;
    final int size = this.settings.minimapSize() + 2 * INSET;
    final int left = this.settings.minimapLeft() ? MARGIN : graphics.guiWidth() - MARGIN - size;
    final int right = left + size;
    final int bottom = top + size;
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, left, top, size, size);
    this.view.draw(
        graphics,
        map,
        left + INSET,
        top + INSET,
        right - INSET,
        bottom - INSET,
        -1,
        -1,
        partialTick);

    CompassButton.drawNeedle(
        graphics,
        right - INSET - NEEDLE_INSET,
        top + INSET + NEEDLE_INSET,
        Projection.topDown(map.config()).bearing(180) + this.view.spin());
    graphics.centeredText(
        minecraft.font,
        "%d %d %d".formatted(player.getBlockX(), player.getBlockY(), player.getBlockZ()),
        (left + right) / 2,
        bottom + 3,
        0xFFFFFFFF);
  }
}
