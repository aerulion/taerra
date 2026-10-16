package net.aerulion.taerra.mod.gui.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.aerulion.taerra.mod.config.MinimapInfo;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Minimap implements HudElement {

  private static final Identifier FRAME =
      Identifier.withDefaultNamespace("container/inventory/effect_background");
  private static final int MARGIN = 4;
  private static final int INSET = 3;
  private static final int GAP = 3;
  private static final int LINE = 10;
  private static final int PADDING = 2;
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
              ? MARGIN + this.settings.minimapSize() + 2 * INSET + infoHeight()
              : 0;
      graphics.pose().pushMatrix();
      graphics.pose().translate(0, shift);
      effects.extractRenderState(graphics, deltaTracker);
      graphics.pose().popMatrix();
    };
  }

  private int infoHeight() {
    final int lines = lines().size();
    return lines == 0 ? 0 : GAP + (int) Math.ceil((lines * LINE - 1) * infoScale());
  }

  private float infoScale() {
    final int gui = (int) Minecraft.getInstance().getWindow().getGuiScale();
    return this.settings.smallInfo() && gui > 1 ? (gui - 1) / (float) gui : 1;
  }

  private @NotNull List<Component> lines() {
    final @NotNull Minecraft minecraft = Minecraft.getInstance();
    final @Nullable ClientLevel level = minecraft.level;
    final @Nullable LocalPlayer player = minecraft.player;
    final @NotNull List<Component> lines = new ArrayList<>();
    if (level == null || player == null) {
      return lines;
    }
    for (final @NotNull MinimapInfo info : MinimapInfo.values()) {
      final @Nullable Component line =
          this.settings.minimapInfo(info) ? text(info, minecraft, level, player) : null;
      if (line != null) {
        lines.add(line);
      }
    }
    return lines;
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
    drawInfo(graphics, minecraft, (left + right) / 2, bottom + GAP);
  }

  private void drawInfo(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull Minecraft minecraft,
      final int center,
      final int top) {
    final @NotNull List<Component> lines = lines();
    if (lines.isEmpty()) {
      return;
    }
    final @NotNull Font font = minecraft.font;
    final float scale = infoScale();
    final int screen = (int) (graphics.guiWidth() / scale);
    final int width = lines.stream().mapToInt(font::width).max().orElse(0);
    final int half = (width + 1) / 2;
    final int x =
        Math.max(PADDING + half, Math.min(screen - PADDING - half, Math.round(center / scale)));
    graphics.pose().pushMatrix();
    graphics.pose().translate(0, top);
    graphics.pose().scale(scale, scale);
    final int backdrop = minecraft.options.getBackgroundColor(0f);
    if (backdrop != 0) {
      graphics.fill(
          x - half - PADDING,
          -PADDING,
          x + half + PADDING,
          lines.size() * LINE - 1 + PADDING,
          backdrop);
    }
    for (int i = 0; i < lines.size(); i++) {
      graphics.centeredText(font, lines.get(i), x, i * LINE, 0xFFFFFFFF);
    }
    graphics.pose().popMatrix();
  }

  private static @Nullable Component text(
      final @NotNull MinimapInfo info,
      final @NotNull Minecraft minecraft,
      final @NotNull ClientLevel level,
      final @NotNull LocalPlayer player) {
    final @NotNull BlockPos at = player.blockPosition();
    return switch (info) {
      case COORDINATES -> Component.literal("%d %d %d".formatted(at.getX(), at.getY(), at.getZ()));
      case NETHER -> {
        if (level.dimension() == Level.OVERWORLD) {
          yield Component.translatable(
              "taerra.minimap.nether", Math.floorDiv(at.getX(), 8), Math.floorDiv(at.getZ(), 8));
        }
        if (level.dimension() == Level.NETHER) {
          yield Component.translatable("taerra.minimap.overworld", at.getX() * 8, at.getZ() * 8);
        }
        yield null;
      }
      case CHUNK ->
          Component.translatable(
              "taerra.minimap.chunk",
              SectionPos.blockToSectionCoord(at.getX()),
              SectionPos.blockToSectionCoord(at.getZ()));
      case FACING ->
          Component.translatable(
              "taerra.minimap.facing."
                  + Math.floorMod(Math.round((player.getYRot() - 180) / 45), 8));
      case BIOME -> biome(level.getBiome(at));
      case WEATHER -> weather(level, at);
      case TIME ->
          day(level)
              .map(
                  ticks -> {
                    final long time = ticks.getCurrentTicks(level.clockManager()) + 6000;
                    return (Component)
                        Component.literal(
                            "%d:%02d".formatted(time / 1000 % 24, time % 1000 * 60 / 1000));
                  })
              .orElse(null);
      case DAY ->
          day(level)
              .map(
                  ticks ->
                      (Component)
                          Component.translatable(
                              "taerra.minimap.day", ticks.getPeriodCount(level.clockManager())))
              .orElse(null);
      case LIGHT ->
          Component.translatable("taerra.minimap.light", level.getBrightness(LightLayer.BLOCK, at));
      case REAL_TIME ->
          Component.translatable(
              "taerra.minimap.real_time",
              LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
      case FPS -> Component.translatable("taerra.minimap.fps", minecraft.getFps());
    };
  }

  private static @Nullable Component weather(
      final @NotNull ClientLevel level, final @NotNull BlockPos at) {
    if (!level.dimensionType().hasSkyLight()) {
      return null;
    }
    final @NotNull Biome biome = level.getBiome(at).value();
    if (!level.isRaining() || !biome.hasPrecipitation()) {
      return Component.translatable("taerra.minimap.weather.clear");
    }
    if (level.isThundering()) {
      return Component.translatable("taerra.minimap.weather.thunder");
    }
    return Component.translatable(
        biome.coldEnoughToSnow(at, level.getSeaLevel())
            ? "taerra.minimap.weather.snow"
            : "taerra.minimap.weather.rain");
  }

  private static @NotNull Optional<Timeline> day(final @NotNull ClientLevel level) {
    return level.registryAccess().get(Timelines.OVERWORLD_DAY).map(Holder::value);
  }

  private static @NotNull Component biome(final @NotNull Holder<Biome> biome) {
    return biome
        .unwrapKey()
        .map(
            key -> {
              final @NotNull Identifier id = key.identifier();
              final @NotNull String path =
                  id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
              return (Component)
                  Component.translatableWithFallback(
                      Util.makeDescriptionId("biome", id),
                      Arrays.stream(path.split("_"))
                          .filter(word -> !word.isEmpty())
                          .map(
                              word ->
                                  word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                          .collect(Collectors.joining(" ")));
            })
        .orElseGet(Component::empty);
  }
}
