package net.aerulion.taerra.mod.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.LongPredicate;
import net.aerulion.taerra.mod.TaerraClient;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.pick.PickCache;
import net.aerulion.taerra.mod.tile.TileKey;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.PlayerInfos;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.Marker;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.Regions;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

public final class MapViewImpl implements MapView {

  private static final int SELECTION_FILL = 0x40FFFFFF;
  private static final int SELECTION_LINE = 0xC0FFFFFF;
  private static final int ERASE_LINE = 0xFFFF5555;
  private static final int MARKER_FILL = 0x50;
  private static final int OTHER_FILL = 0x38;
  private static final int ERASE_FILL = 0x80FF5555;
  private static final int HOVER_ERASE_FILL = 0x40000000;
  private static final int UNKNOWN_GROUND = 63;
  private static final int HOVER_RADIUS = 5;
  private static final int HIT_PIXELS = 3;
  private static final long GROUND_DEBOUNCE_MILLIS = 250;
  private static final int CULL_MARGIN = 64;
  private static final int LABEL_GAP = 3;
  private static final int REGION_LABEL_Y = 64;
  private static final int FALLBACK_LEVELS = 4;
  private static final double ZOOM_EASE = 18;
  private static final double GLIDE_FRICTION = 6;

  private final Settings settings;
  private final boolean minimap;
  private final Map<Marker, MarkerShape> shapes = new IdentityHashMap<>();
  private final List<double[]> labels = new ArrayList<>();
  private final MarkerOverlay overlay = new MarkerOverlay();
  private @Nullable String world;
  private boolean isometric;
  private int rotation;
  private double spin;
  private double zoom;
  private double targetZoom;
  private double zoomAnchorX;
  private double zoomAnchorY;
  private double velocityX;
  private double velocityY;
  private long lastFrame;
  private double centerX;
  private double centerY;
  private double centerHeight = 64;
  private double screenX;
  private double screenY;
  private double unitPixels = 1;
  private double pixels = 1;
  private double subPixelX;
  private double subPixelY;
  private double offsetX;
  private double offsetY;
  private int maxZoom;
  private @NotNull Look look = Look.COLOR;
  private @Nullable List<Component> tooltip;
  private @Nullable MarkerHover hover;
  private final Map<Long, Integer> surfaces = new HashMap<>();
  private final Map<Long, Integer> levels = new HashMap<>();
  private final Map<String, List<List<double[]>>> drapes = new HashMap<>();
  private @Nullable Drawing drawing;
  private @Nullable List<Layer> hiddenFrom;
  private @Nullable String hiddenId;
  private @NotNull List<Layer> withoutHidden = List.of();
  private final List<double[]> pathScreen = new ArrayList<>();
  private @Nullable Projection shaped;
  private @Nullable List<Layer> shapedLayers;
  private @Nullable OverlayScene scene;
  private @Nullable List<Layer> sceneLayers;
  private boolean @NotNull [] sceneShown = new boolean[0];
  private int sceneGrounds;
  private long sceneBuilt;
  private int grounds;

  public MapViewImpl(final Settings settings, final double zoom, final boolean minimap) {
    this.settings = settings;
    this.zoom = zoom;
    this.targetZoom = zoom;
    this.minimap = minimap;
  }

  private static boolean hovers(
      final double @NotNull [] at, final double mouseX, final double mouseY) {
    return Math.abs(mouseX - at[0]) <= HOVER_RADIUS && Math.abs(mouseY - at[1]) <= HOVER_RADIUS;
  }

  private static boolean tiny(final @NotNull MarkerShape shape, final double gui) {
    return shape.face != null && shape.extent() * gui < OverlayRaster.TINY;
  }

  @Override
  public @Nullable String world() {
    return this.world;
  }

  @Override
  public void setWorld(final @Nullable String world) {
    this.world = world;
  }

  @Override
  public boolean isometric() {
    return this.isometric;
  }

  @Override
  public int rotation() {
    return this.rotation;
  }

  @Override
  public double spin() {
    return this.spin;
  }

  @Override
  public void setSpin(final double spin) {
    this.spin = spin;
  }

  @Override
  public @NotNull Projection projection(final @NotNull MapConfig config) {
    return Projection.of(config, this.isometric, this.rotation);
  }

  @Override
  public void centerOn(
      final @NotNull MapConfig config, final double x, final double y, final double z) {
    final double @NotNull [] map = projection(config).toMap(x, y, z);
    this.centerX = map[0];
    this.centerY = map[1];
    this.centerHeight = y;
    stop();
  }

  @Override
  public void setView(
      final @NotNull MapConfig config, final boolean isometric, final int rotation) {
    final double @NotNull [] center =
        projection(config).toBlock(this.centerX, this.centerY, this.centerHeight);
    if (isometric != this.isometric) {
      final double isoBlock =
          Projection.isometric(config, 0).unitsPerBlock() * config.isometric().blockWidth();
      final double offset =
          Math.round(Math.log(isoBlock / Projection.topDown(config).unitsPerBlock()) / Math.log(2));
      this.zoom += isometric ? -offset : offset;
      this.targetZoom = Math.round(this.zoom);
    }
    this.isometric = isometric;
    this.rotation = rotation;
    centerOn(config, center[0], this.centerHeight, center[1]);
  }

  @Override
  public void pan(final double dx, final double dy) {
    this.centerX -= dx / this.unitPixels;
    this.centerY -= dy / this.unitPixels;
  }

  @Override
  public void glide(final double dx, final double dy) {
    this.velocityX = dx;
    this.velocityY = dy;
  }

  @Override
  public void stop() {
    this.velocityX = 0;
    this.velocityY = 0;
  }

  @Override
  public void zoomAt(final double steps, final double x, final double y) {
    this.targetZoom = Math.round(this.targetZoom + steps);
    this.zoomAnchorX = x;
    this.zoomAnchorY = y;
  }

  @Override
  public void zoomToward(final double target) {
    this.targetZoom = target;
    this.zoomAnchorX = this.screenX;
    this.zoomAnchorY = this.screenY;
  }

  @Override
  public long targetZoom() {
    return Math.round(this.targetZoom);
  }

  @Override
  public @Nullable Place placeAt(final @NotNull WebMap map, final double x, final double y) {
    final @Nullable MapConfig config = map.config();
    if (config == null || this.world == null) {
      return null;
    }
    final double mapX = this.centerX + (x - this.screenX) / this.unitPixels;
    final double mapY = this.centerY + (y - this.screenY) / this.unitPixels;
    final @NotNull Projection projection = projection(config);
    if (this.isometric) {
      final int @Nullable [] block =
          map.picks()
              .isometric(
                  this.world,
                  this.rotation,
                  (int) Math.floor(mapX / projection.unitsPerBlock()),
                  (int) Math.floor(mapY / projection.unitsPerBlock()));
      return block == null ? null : new Place(block[0], block[1], block[2]);
    }
    final int blockX = (int) Math.floor(mapX / projection.unitsPerBlock());
    final int blockZ = (int) Math.floor(mapY / projection.unitsPerBlock());
    final @Nullable OptionalInt surface = map.picks().surface(this.world, blockX, blockZ);
    return new Place(
        blockX, surface == null || surface.isEmpty() ? null : surface.getAsInt(), blockZ);
  }

  @Override
  public @Nullable MarkerHover hover() {
    return this.hover;
  }

  @Override
  public void select(final @Nullable Drawing drawing) {
    if (drawing == null || this.drawing == null || drawing.chunks() != this.drawing.chunks()) {
      this.surfaces.clear();
      this.levels.clear();
    }
    this.drapes.clear();
    this.drawing = drawing;
  }

  @Override
  public int pathSegmentAt(final double x, final double y) {
    final double @NotNull [] mouse = unturn(x, y);
    final int count = this.pathScreen.size();
    final boolean closed = this.drawing != null && this.drawing.closed() && count >= 3;
    for (int i = 0; i < count - (closed ? 0 : 1); i++) {
      if (Geometry.near(
          this.pathScreen.get(i),
          this.pathScreen.get((i + 1) % count),
          mouse[0],
          mouse[1],
          HIT_PIXELS)) {
        return i;
      }
    }
    return -1;
  }

  private @NotNull List<Layer> without(
      final @NotNull List<Layer> layers, final @Nullable String hidden) {
    if (hidden == null) {
      return layers;
    }
    if (layers != this.hiddenFrom || !hidden.equals(this.hiddenId)) {
      this.hiddenFrom = layers;
      this.hiddenId = hidden;
      this.withoutHidden =
          layers.stream()
              .map(
                  layer ->
                      new Layer(
                          layer.id(),
                          layer.label(),
                          layer.shown(),
                          layer.restricted(),
                          layer.personal(),
                          layer.markers().stream()
                              .filter(marker -> !marker.id().equals(hidden))
                              .toList()))
              .toList();
    }
    return this.withoutHidden;
  }

  @Override
  public int pathPointAt(final double x, final double y) {
    final double @NotNull [] mouse = unturn(x, y);
    for (int i = this.pathScreen.size() - 1; i >= 0; i--) {
      if (hovers(this.pathScreen.get(i), mouse[0], mouse[1])) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void draw(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final int x0,
      final int y0,
      final int x1,
      final int y1,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    this.tooltip = null;
    this.hover = null;
    final @Nullable MapConfig config = map.config();
    final @Nullable World world = map.world(this.world);
    if (config == null || world == null) {
      return;
    }
    final @NotNull Projection projection = projection(config);
    this.maxZoom = this.isometric ? config.isometric().maxZoom() : config.maxZoom();
    this.look = this.settings.grayMap() ? Look.GRAY : Look.COLOR;
    final int minZoom = world.minZoom(config, this.isometric, this.rotation);
    this.screenX = (x0 + x1) / 2.0;
    this.screenY = (y0 + y1) / 2.0;
    this.pixels = Minecraft.getInstance().getWindow().getGuiScale();
    final double closest =
        Math.max(
            minZoom - 1,
            Projection.closestZoom(
                config, this.isometric, Minecraft.getInstance().getWindow().getGuiScale()));
    this.targetZoom = Math.clamp(this.targetZoom, minZoom - 1, closest);
    this.zoom = Math.clamp(this.zoom, minZoom - 1, closest);
    this.unitPixels = unitPixels();
    animate();
    if (!this.minimap && world.bounds() != null) {
      keepInside(world.bounds(), projection);
    }
    final double originX = snap(this.screenX - this.centerX * this.unitPixels);
    final double originY = snap(this.screenY - this.centerY * this.unitPixels);
    this.subPixelX = originX - Math.floor(originX);
    this.subPixelY = originY - Math.floor(originY);
    this.offsetX = originX - this.subPixelX;
    this.offsetY = originY - this.subPixelY;
    final double @NotNull [] mouse = unturn(mouseX, mouseY);

    final double reach = Math.hypot(x1 - x0, y1 - y0) / 2;
    final double left = this.spin == 0 ? this.screenX - x0 : reach;
    final double right = this.spin == 0 ? x1 - this.screenX : reach;
    final double up = this.spin == 0 ? this.screenY - y0 : reach;
    final double down = this.spin == 0 ? y1 - this.screenY : reach;
    final double @NotNull [] view = {
      this.centerX - left / this.unitPixels,
      this.centerY - up / this.unitPixels,
      this.centerX + right / this.unitPixels,
      this.centerY + down / this.unitPixels
    };

    graphics.enableScissor(x0, y0, x1, y1);
    if (this.minimap) {
      graphics.fill(x0, y0, x1, y1, world.voidColor());
    }
    drawTiles(graphics, map, world, minZoom, view);
    final List<Layer> layers =
        without(map.markers(world.name()), this.drawing == null ? null : this.drawing.hidden());
    if (layers != this.shapedLayers || !projection.equals(this.shaped)) {
      this.shapes.clear();
      this.shapedLayers = layers;
      this.shaped = projection;
      for (final @NotNull Layer layer : layers) {
        for (final @NotNull Marker marker : layer.markers()) {
          this.shapes.put(marker, new MarkerShape(marker, projection));
        }
      }
    }
    final int level = Math.clamp(Math.round(this.zoom), minZoom - 1, this.maxZoom + 2);
    final boolean settled =
        level == Math.clamp(Math.round(this.targetZoom), minZoom - 1, this.maxZoom + 2);
    final boolean far = OverlayRaster.far(this.maxZoom, level);
    final double gui = OverlayRaster.guiPerUnit(level, this.pixels);
    final double margin = CULL_MARGIN / this.unitPixels;
    final double @NotNull [] cull = {
      view[0] - margin, view[1] - margin, view[2] + margin, view[3] + margin
    };
    final double pointerX = (mouse[0] - this.offsetX) / this.unitPixels;
    final double pointerY = (mouse[1] - this.offsetY) / this.unitPixels;
    final double hit = HIT_PIXELS / this.unitPixels;
    final @NotNull List<DrawnPoint> points = new ArrayList<>();
    @Nullable DrawnPoint hovered = null;
    for (final @NotNull Layer layer : layers) {
      if (!map.shown(layer)) {
        continue;
      }
      for (final @NotNull Marker marker : layer.markers()) {
        final MarkerShape shape = this.shapes.get(marker);
        if (shape == null || !shape.reaches(cull)) {
          continue;
        }
        if (shape.point != null) {
          points.add(new DrawnPoint(layer, marker, shape));
          continue;
        }
        final boolean tiny = tiny(shape, gui);
        if (this.isometric && shape.face != null && shape.ground == null && !tiny) {
          requestGround(map, marker, shape);
        }
        if ((!"regions".equals(layer.id()) || marker.label() != null && !marker.label().isEmpty())
            && hits(shape, marker, pointerX, pointerY, hit, far, tiny)) {
          hovered = new DrawnPoint(layer, marker, shape);
        }
      }
    }
    updateScene(map, world.name(), projection, layers);

    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    turn(pose);
    this.overlay.draw(
        graphics,
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
        level,
        this.offsetX + this.subPixelX,
        this.offsetY + this.subPixelY,
        this.unitPixels,
        view,
        settled);
    pose.popMatrix();

    pose.pushMatrix();
    turn(pose);
    pose.translate((float) this.subPixelX, (float) this.subPixelY);
    if (hovered != null) {
      final int top = this.spin == 0 ? y0 : 0;
      final int bottom = this.spin == 0 ? y1 : graphics.guiHeight();
      highlight(graphics, hovered, far, tiny(hovered.shape(), gui), top, bottom);
      hovered(hovered.layer(), hovered.marker(), hovered.shape());
    }
    if (this.drawing != null) {
      drawSelection(
          graphics,
          map,
          this.drawing,
          projection,
          this.spin == 0 ? y0 : 0,
          this.spin == 0 ? y1 : graphics.guiHeight());
    }
    drawRegionNames(graphics, map, projection);
    drawPoints(graphics, points, mouse);
    drawTarget(graphics, map, projection, x0, y0, x1, y1);
    drawPlayers(graphics, map, projection, x0, y0, x1, y1, mouse, partialTick);
    pose.popMatrix();
    graphics.disableScissor();
    if (this.tooltip != null && mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
      graphics.setComponentTooltipForNextFrame(
          Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
    }
  }

  private void keepInside(
      final World.@NotNull Bounds bounds, final @NotNull Projection projection) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (final int x : new int[] {bounds.minX(), bounds.maxX() + 1}) {
      for (final int z : new int[] {bounds.minZ(), bounds.maxZ() + 1}) {
        final double @NotNull [] corner = projection.toMap(x, this.centerHeight, z);
        minX = Math.min(minX, corner[0]);
        minY = Math.min(minY, corner[1]);
        maxX = Math.max(maxX, corner[0]);
        maxY = Math.max(maxY, corner[1]);
      }
    }
    this.centerX = Math.clamp(this.centerX, minX, maxX);
    this.centerY = Math.clamp(this.centerY, minY, maxY);
  }

  private double unitPixels() {
    return Math.pow(2, this.zoom) / Minecraft.getInstance().getWindow().getGuiScale();
  }

  private void zoomTo(final double zoom) {
    final double pointX = this.centerX + (this.zoomAnchorX - this.screenX) / this.unitPixels;
    final double pointY = this.centerY + (this.zoomAnchorY - this.screenY) / this.unitPixels;
    this.zoom = zoom;
    this.unitPixels = unitPixels();
    this.centerX = pointX - (this.zoomAnchorX - this.screenX) / this.unitPixels;
    this.centerY = pointY - (this.zoomAnchorY - this.screenY) / this.unitPixels;
  }

  private void animate() {
    final long now = System.nanoTime();
    final double seconds = this.lastFrame == 0 ? 0 : Math.min((now - this.lastFrame) / 1e9, .1);
    this.lastFrame = now;
    if (this.zoom != this.targetZoom) {
      final double eased =
          this.zoom + (this.targetZoom - this.zoom) * (1 - Math.exp(-ZOOM_EASE * seconds));
      zoomTo(Math.abs(this.targetZoom - eased) < .005 ? this.targetZoom : eased);
    }
    if (this.velocityX != 0 || this.velocityY != 0) {
      pan(this.velocityX * seconds, this.velocityY * seconds);
      final double friction = Math.exp(-GLIDE_FRICTION * seconds);
      this.velocityX *= friction;
      this.velocityY *= friction;
      if (Math.hypot(this.velocityX, this.velocityY) < 5) {
        stop();
      }
    }
  }

  private double[] toScreen(final double @NotNull [] map) {
    return new double[] {
      this.offsetX + map[0] * this.unitPixels, this.offsetY + map[1] * this.unitPixels
    };
  }

  private double[][] screen(final double @NotNull [][] map) {
    final double step = 1 / this.pixels;
    final double @NotNull [][] kept = new double[map.length][];
    int count = 0;
    for (final double @NotNull [] point : map) {
      final double @NotNull [] at = toScreen(point);
      if (count == 0
          || Math.abs(at[0] - kept[count - 1][0]) >= step
          || Math.abs(at[1] - kept[count - 1][1]) >= step) {
        kept[count] = at;
        count++;
      }
    }
    return count == map.length ? kept : Arrays.copyOf(kept, count);
  }

  private void turn(final @NotNull Matrix3x2fStack pose) {
    if (this.spin != 0) {
      pose.translate((float) this.screenX, (float) this.screenY);
      pose.rotate((float) this.spin);
      pose.translate((float) -this.screenX, (float) -this.screenY);
    }
  }

  private double[] unturn(final double x, final double y) {
    final double cos = Math.cos(-this.spin);
    final double sin = Math.sin(-this.spin);
    final double dx = x - this.screenX;
    final double dy = y - this.screenY;
    return new double[] {
      this.screenX + dx * cos - dy * sin - this.subPixelX,
      this.screenY + dx * sin + dy * cos - this.subPixelY
    };
  }

  private double[] offMap(
      final double @NotNull [] shown, final int x0, final int y0, final int x1, final int y1) {
    if (!this.settings.offMapDirection()) {
      return unturn(Math.clamp(shown[0], x0 + 4, x1 - 4), Math.clamp(shown[1], y0 + 4, y1 - 4));
    }
    final double dx = shown[0] - this.screenX;
    final double dy = shown[1] - this.screenY;
    final double scale =
        Math.min(
            1,
            Math.min(((x1 - x0) / 2.0 - 4) / Math.abs(dx), ((y1 - y0) / 2.0 - 4) / Math.abs(dy)));
    return unturn(this.screenX + dx * scale, this.screenY + dy * scale);
  }

  private double[] onScreen(final double @NotNull [] point) {
    final double cos = Math.cos(this.spin);
    final double sin = Math.sin(this.spin);
    final double dx = point[0] + this.subPixelX - this.screenX;
    final double dy = point[1] + this.subPixelY - this.screenY;
    return new double[] {this.screenX + dx * cos - dy * sin, this.screenY + dx * sin + dy * cos};
  }

  private double snap(final double value) {
    return Math.round(value * this.pixels) / this.pixels;
  }

  private double[] snapped(final double x, final double y) {
    if (this.spin != 0) {
      return new double[] {x, y};
    }
    return new double[] {
      snap(x + this.subPixelX) - this.subPixelX, snap(y + this.subPixelY) - this.subPixelY
    };
  }

  private void fill(
      final @NotNull GuiGraphicsExtractor graphics,
      final double @NotNull [][] points,
      final int top,
      final int bottom,
      final int color) {
    Geometry.fillPolygon(
        graphics, points, top, bottom, color, this.subPixelX, this.subPixelY, this.pixels);
  }

  private void icon(
      final @NotNull GuiGraphicsExtractor graphics,
      final double[] point,
      final double angle,
      final @NotNull Runnable draw) {
    final double[] at = angle == 0 ? snapped(point[0], point[1]) : point;
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate((float) at[0], (float) at[1]);
    pose.rotate((float) (angle - this.spin));
    pose.translate(-4, -4);
    draw.run();
    pose.popMatrix();
  }

  private void drawTiles(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull World world,
      final int minZoom,
      final double @NotNull [] view) {
    final int level = Math.clamp(Math.round(this.zoom), minZoom, this.maxZoom);
    final boolean settled = level == Math.clamp(Math.round(this.targetZoom), minZoom, this.maxZoom);
    final int size = (int) Projection.tileUnits(level);
    final long centerTileX = (long) Math.floor(this.centerX / size);
    final long centerTileY = (long) Math.floor(this.centerY / size);
    final int firstX = (int) Math.floor(view[0] / size);
    final int lastX = (int) Math.floor(view[2] / size);
    final int firstY = (int) Math.floor(view[1] / size);
    final int lastY = (int) Math.floor(view[3] / size);

    final @NotNull GpuSampler sampler =
        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    turn(pose);
    pose.translate(
        (float) (this.offsetX + this.subPixelX + centerTileX * (double) size * this.unitPixels),
        (float) (this.offsetY + this.subPixelY + centerTileY * (double) size * this.unitPixels));
    pose.scale((float) this.unitPixels);
    final @NotNull List<TileKey> missing = new ArrayList<>();
    for (int x = firstX; x <= lastX; x++) {
      for (int y = firstY; y <= lastY; y++) {
        final @NotNull TileKey key =
            new TileKey(
                world.name(), this.isometric, this.isometric ? this.rotation : 0, level, x, y);
        final int tileLeft = (int) (x - centerTileX) * size;
        final int tileTop = (int) (y - centerTileY) * size;
        final @Nullable GpuTextureView texture = map.tiles().get(key);
        if (settled && map.tiles().wanted(key)) {
          missing.add(key);
        }
        if (texture != null) {
          graphics.blit(
              texture, sampler, tileLeft, tileTop, tileLeft + size, tileTop + size, 0, 1, 0, 1);
          continue;
        }
        @NotNull TileKey above = key;
        boolean covered = false;
        for (int up = 1; up <= FALLBACK_LEVELS && above.zoom() > minZoom && !covered; up++) {
          above = above.parent();
          final @Nullable GpuTextureView fallback = map.tiles().peek(above);
          if (fallback != null) {
            final float part = 1f / (1 << up);
            final float u = (x - (above.x() << up)) * part;
            final float v = (y - (above.y() << up)) * part;
            graphics.blit(
                fallback,
                sampler,
                tileLeft,
                tileTop,
                tileLeft + size,
                tileTop + size,
                u,
                u + part,
                v,
                v + part);
            covered = true;
          }
        }
        final int half = size / 2;
        for (int child = 0; child < 4 && !covered && half > 0 && level < this.maxZoom; child++) {
          final @Nullable GpuTextureView fallback =
              map.tiles().peek(key.child(child & 1, child >> 1));
          if (fallback != null) {
            final int childLeft = tileLeft + (child & 1) * half;
            final int childTop = tileTop + (child >> 1) * half;
            graphics.blit(
                fallback,
                sampler,
                childLeft,
                childTop,
                childLeft + half,
                childTop + half,
                0,
                1,
                0,
                1);
          }
        }
      }
    }
    pose.popMatrix();
    final double middleX = this.centerX / size - .5;
    final double middleY = this.centerY / size - .5;
    missing.sort(
        Comparator.comparingDouble(key -> Math.hypot(key.x() - middleX, key.y() - middleY)));
    for (final TileKey key : missing) {
      if (!map.tiles().request(key)) {
        break;
      }
    }
  }

  private void hovered(
      final @NotNull Layer layer, final @NotNull Marker marker, final @NotNull MarkerShape shape) {
    this.tooltip = shape.tooltip(layer, marker);
    this.hover = new MarkerHover(layer, marker);
  }

  private double @Nullable [][] outline(
      final @NotNull MarkerShape shape, final @NotNull Marker marker, final boolean far) {
    if (!this.isometric) {
      return shape.face;
    }
    if (shape.ground == null) {
      return null;
    }
    return far || marker.fullHeight() ? shape.ground : shape.face;
  }

  private boolean hits(
      final @NotNull MarkerShape shape,
      final @NotNull Marker marker,
      final double x,
      final double y,
      final double reach,
      final boolean far,
      final boolean tiny) {
    if (!shape.near(x, y, reach)) {
      return false;
    }
    if (shape.path != null) {
      return Geometry.nearPath(shape.path, x, y, reach);
    }
    if (tiny) {
      return true;
    }
    final double @Nullable [][] outline = outline(shape, marker, far);
    if (outline == null) {
      return false;
    }
    return marker.hollow()
        ? Geometry.nearRing(outline, x, y, reach)
        : Geometry.contains(outline, x, y);
  }

  private void requestGround(
      final @NotNull WebMap map, final @NotNull Marker marker, final @NotNull MarkerShape shape) {
    if (shape.groundKey == null) {
      shape.groundKey = PickCache.groundKey(this.world, marker);
    }
    final @Nullable List<List<double[]>> sides =
        map.picks().ground(shape.groundKey, this.world, marker);
    if (sides != null) {
      shape.stand(marker, sides, this.shaped);
      this.grounds++;
    }
  }

  private void updateScene(
      final @NotNull WebMap map,
      final @NotNull String world,
      final @NotNull Projection projection,
      final @NotNull List<Layer> layers) {
    final boolean @NotNull [] shown = new boolean[layers.size()];
    for (int i = 0; i < shown.length; i++) {
      shown[i] = map.shown(layers.get(i));
    }
    final @Nullable OverlayScene current = this.scene;
    final boolean changed =
        current == null
            || !world.equals(current.world())
            || !projection.equals(current.projection())
            || layers != this.sceneLayers
            || !Arrays.equals(shown, this.sceneShown)
            || this.look != current.look()
            || this.pixels != current.pixels()
            || this.maxZoom != current.maxZoom();
    final long now = System.currentTimeMillis();
    if (!changed
        && (this.grounds == this.sceneGrounds || now - this.sceneBuilt < GROUND_DEBOUNCE_MILLIS)) {
      return;
    }
    final @NotNull List<OverlayItem> items = new ArrayList<>();
    for (int i = 0; i < shown.length; i++) {
      if (!shown[i]) {
        continue;
      }
      final boolean personal = layers.get(i).personal();
      for (final @NotNull Marker marker : layers.get(i).markers()) {
        final MarkerShape shape = this.shapes.get(marker);
        final @Nullable OverlayItem item =
            shape == null ? null : OverlayItem.of(marker, shape, personal);
        if (item != null) {
          items.add(item);
        }
      }
    }
    this.scene =
        new OverlayScene(
            current == null ? 0 : current.version() + 1,
            world,
            projection,
            List.copyOf(items),
            this.look,
            this.pixels,
            this.maxZoom);
    this.sceneLayers = layers;
    this.sceneShown = shown;
    this.sceneGrounds = this.grounds;
    this.sceneBuilt = now;
    this.overlay.setScene(this.scene);
  }

  private void highlight(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull DrawnPoint hovered,
      final boolean far,
      final boolean tiny,
      final int top,
      final int bottom) {
    final MarkerShape shape = hovered.shape();
    if (shape.path != null) {
      final double @NotNull [][] path = screen(shape.path);
      for (int i = 0; i + 1 < path.length; i++) {
        Geometry.line(graphics, path[i], path[i + 1], 3, shape.color);
      }
      return;
    }
    final double @Nullable [][] outline = tiny ? null : outline(shape, hovered.marker(), far);
    if (outline == null) {
      return;
    }
    final double @NotNull [][] face = screen(outline);
    if (hovered.marker().hollow()) {
      Geometry.ring(
          graphics,
          face,
          (far ? 1 : 2) + 1,
          far ? Colors.withAlpha(OverlayRaster.FAR_LINE, shape.color) : shape.color);
      return;
    }
    final int extra =
        (int)
            Math.round(
                255.0 * (this.look.hoverFill() - this.look.fill()) / (255 - this.look.fill()));
    fill(graphics, face, top, bottom, Colors.withAlpha(extra, shape.color));
  }

  private void drawSelection(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Drawing drawing,
      final @NotNull Projection projection,
      final int top,
      final int bottom) {
    final int line = drawing.color() == null ? SELECTION_LINE : drawing.color();
    final int area =
        drawing.color() == null ? SELECTION_FILL : Colors.withAlpha(MARKER_FILL, drawing.color());
    for (final Drawing.@NotNull Area other : drawing.others()) {
      region(
          graphics,
          map,
          projection,
          other.rings(),
          other.chunks()::contains,
          false,
          top,
          bottom,
          Colors.withAlpha(OTHER_FILL, other.color()),
          0xFF000000 | other.color());
    }
    final @NotNull ChunkSelection chunks = drawing.chunks();
    region(
        graphics,
        map,
        projection,
        chunks.settledRings(),
        chunks::containsChunk,
        drawing.closed(),
        top,
        bottom,
        area,
        line);
    squares(graphics, map, projection, chunks.strokeAdded(), top, bottom, area, 0);
    squares(graphics, map, projection, chunks.strokeRemoved(), top, bottom, ERASE_FILL, ERASE_LINE);
    if (!chunks.stroking()) {
      squares(graphics, map, projection, chunks.detached(), top, bottom, ERASE_FILL, ERASE_LINE);
    }
    final @NotNull List<Long> free = new ArrayList<>();
    final @NotNull List<Long> taken = new ArrayList<>();
    for (final long chunk : drawing.hover()) {
      (!drawing.hoverErase()
                  && chunks.taken(ChunkSelection.chunkX(chunk), ChunkSelection.chunkZ(chunk))
              ? taken
              : free)
          .add(chunk);
    }
    squares(
        graphics,
        map,
        projection,
        free,
        top,
        bottom,
        drawing.hoverErase() ? HOVER_ERASE_FILL : 0,
        drawing.hoverErase() ? ERASE_LINE : SELECTION_LINE);
    squares(graphics, map, projection, taken, top, bottom, ERASE_FILL, ERASE_LINE);
    final double @Nullable [][] outline = drawing.stroke();
    if (outline != null && outline.length > 1) {
      final double @NotNull [][] path = new double[outline.length][];
      for (int i = 0; i < outline.length; i++) {
        path[i] = level(map, projection, outline[i][0], outline[i][1], null);
      }
      Geometry.ring(graphics, screen(path), 1, drawing.erasing() ? ERASE_LINE : SELECTION_LINE);
    }
    this.pathScreen.clear();
    final @Nullable List<List<double[]>> draped =
        drawing.closed() ? drape(map, projection, drawing.points()) : null;
    if (draped != null) {
      for (final @NotNull List<double[]> side : draped) {
        this.pathScreen.add(toScreen(side.getFirst()));
      }
      final double @NotNull [][] face = screen(flat(draped));
      fill(graphics, face, top, bottom, area);
      Geometry.ring(graphics, face, 2, line);
    } else {
      for (final double @NotNull [] point : drawing.points()) {
        this.pathScreen.add(toScreen(ground(map, projection, point[0], point[1])));
      }
      if (drawing.closed() && this.pathScreen.size() >= 3) {
        final double @NotNull [][] face = this.pathScreen.toArray(double[][]::new);
        fill(graphics, face, top, bottom, area);
        Geometry.ring(graphics, face, 2, line);
      } else if (drawing.smooth() && drawing.points().size() > 2) {
        final @NotNull List<double[]> curve =
            Spline.through(
                drawing.points().stream()
                    .map(point -> new double[] {point[0], 0, point[1]})
                    .toList());
        for (int i = 1; i < curve.size(); i++) {
          Geometry.line(
              graphics,
              toScreen(ground(map, projection, curve.get(i - 1)[0], curve.get(i - 1)[2])),
              toScreen(ground(map, projection, curve.get(i)[0], curve.get(i)[2])),
              2,
              line);
        }
      } else {
        for (int i = 1; i < this.pathScreen.size(); i++) {
          Geometry.line(graphics, this.pathScreen.get(i - 1), this.pathScreen.get(i), 2, line);
        }
      }
    }
    for (final double @NotNull [] at : this.pathScreen) {
      final int x = (int) Math.round(at[0]);
      final int y = (int) Math.round(at[1]);
      graphics.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
      graphics.fill(x - 2, y - 2, x + 2, y + 2, 0xFFFFFFFF);
    }
  }

  private void region(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final @NotNull List<int[][]> rings,
      final @NotNull LongPredicate owned,
      final boolean closed,
      final int top,
      final int bottom,
      final int area,
      final int line) {
    if (rings.isEmpty()) {
      return;
    }
    final @NotNull List<double[][]> shapes = new ArrayList<>(rings.size());
    int bridged = 0;
    for (final int @NotNull [][] ring : rings) {
      final @Nullable List<List<double[]>> draped =
          closed ? drape(map, projection, ChunkSelection.corners(ring)) : null;
      final double @NotNull [][] points = draped == null ? new double[ring.length][] : flat(draped);
      for (int i = 0; draped == null && i < ring.length; i++) {
        points[i] = level(map, projection, ring[i][0], ring[i][1], owned);
      }
      final double @NotNull [][] shape = screen(points);
      shapes.add(shape);
      bridged += shape.length + 2;
    }
    final double @NotNull [] anchor = shapes.getFirst()[0];
    final double @NotNull [][] bridge = new double[bridged][];
    int next = 0;
    for (final double @NotNull [][] shape : shapes) {
      bridge[next++] = anchor;
      System.arraycopy(shape, 0, bridge, next, shape.length);
      next += shape.length;
      bridge[next++] = shape[0];
    }
    fill(graphics, bridge, top, bottom, area);
    for (final double @NotNull [][] shape : shapes) {
      Geometry.ring(graphics, shape, 1, line);
    }
  }

  private void squares(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final @NotNull Collection<Long> chunks,
      final int top,
      final int bottom,
      final int area,
      final int line) {
    for (final long chunk : chunks) {
      final int x0 = ChunkSelection.chunkX(chunk) << 4;
      final int z0 = ChunkSelection.chunkZ(chunk) << 4;
      final double y =
          this.isometric && this.world != null
              ? level(map, ChunkSelection.chunkX(chunk), ChunkSelection.chunkZ(chunk)) + 1
              : 0;
      final double @NotNull [][] square =
          screen(
              new double[][] {
                projection.toMap(x0, y, z0),
                projection.toMap(x0 + 16, y, z0),
                projection.toMap(x0 + 16, y, z0 + 16),
                projection.toMap(x0, y, z0 + 16)
              });
      if ((area >>> 24) != 0) {
        fill(graphics, square, top, bottom, area);
      }
      if ((line >>> 24) != 0) {
        Geometry.ring(graphics, square, 1, line);
      }
    }
  }

  private @Nullable List<List<double[]>> drape(
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final @NotNull List<double[]> outline) {
    if (!this.isometric || this.world == null || outline.size() < 3) {
      return null;
    }
    final @Nullable List<List<double[]>> sides =
        this.drapes.computeIfAbsent(
            Arrays.deepToString(outline.toArray()), _ -> map.picks().drape(this.world, outline));
    return sides == null
        ? null
        : sides.stream()
            .map(
                side ->
                    side.stream()
                        .map(
                            at ->
                                projection.toMap(
                                    at[0], Double.isNaN(at[1]) ? UNKNOWN_GROUND + 1 : at[1], at[2]))
                        .toList())
            .toList();
  }

  private static double @NotNull [][] flat(final @NotNull List<List<double[]>> sides) {
    return sides.stream().flatMap(List::stream).toArray(double[][]::new);
  }

  private double[] level(
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final double x,
      final double z,
      final @Nullable LongPredicate owned) {
    if (!this.isometric || this.world == null) {
      return projection.toMap(x, 0, z);
    }
    double sum = 0;
    int count = 0;
    for (final int chunkX : ChunkSelection.touching(x)) {
      for (final int chunkZ : ChunkSelection.touching(z)) {
        if (owned == null || owned.test(ChunkSelection.key(chunkX, chunkZ))) {
          sum += level(map, chunkX, chunkZ);
          count++;
        }
      }
    }
    return projection.toMap(x, sum / count + 1, z);
  }

  private int level(final @NotNull WebMap map, final int chunkX, final int chunkZ) {
    final long key = ChunkSelection.key(chunkX, chunkZ);
    final @Nullable Integer known = this.levels.get(key);
    if (known != null) {
      return known;
    }
    final int[] heights = new int[16];
    int count = 0;
    for (int x = 2; x < 16; x += 4) {
      for (int z = 2; z < 16; z += 4) {
        final @Nullable OptionalInt surface =
            map.picks().surface(this.world, (chunkX << 4) + x, (chunkZ << 4) + z);
        if (surface == null) {
          return UNKNOWN_GROUND;
        }
        if (surface.isPresent()) {
          heights[count++] = surface.getAsInt();
        }
      }
    }
    final int y = count == 0 ? UNKNOWN_GROUND : ChunkSelection.median(heights, count);
    this.levels.put(key, y);
    return y;
  }

  private double[] ground(
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final double x,
      final double z) {
    if (!this.isometric || this.world == null) {
      return projection.toMap(x, 0, z);
    }
    final int blockX = (int) Math.floor(x);
    final int blockZ = (int) Math.floor(z);
    final long key = ChunkSelection.key(blockX, blockZ);
    @Nullable Integer y = this.surfaces.get(key);
    if (y == null) {
      final @Nullable OptionalInt surface = map.picks().surface(this.world, blockX, blockZ);
      y = surface == null || surface.isEmpty() ? UNKNOWN_GROUND : surface.getAsInt();
      if (surface != null) {
        this.surfaces.put(key, y);
      }
    }
    return projection.toMap(x, y + 1, z);
  }

  private void drawRegionNames(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Projection projection) {
    final @Nullable List<Regions.Region> regions =
        this.minimap || this.world == null ? null : map.regions(this.world).regions();
    if (regions == null
        || map.markers(this.world).stream()
            .noneMatch(layer -> layer.id().equals("regions") && map.shown(layer))) {
      return;
    }
    final @NotNull Font font = Minecraft.getInstance().font;
    final @Nullable String hidden = this.drawing == null ? null : this.drawing.hidden();
    for (final Regions.@NotNull Region region : regions) {
      if (region.name().isEmpty()
          || region.label() == null
          || ("region:" + region.id()).equals(hidden)) {
        continue;
      }
      final @NotNull String name = region.name();
      final double @NotNull [] at =
          toScreen(projection.toMap(region.label()[0], REGION_LABEL_Y, region.label()[1]));
      final int width = font.width(name);
      final double @NotNull [] corner = snapped(at[0] - (width + 1) / 2.0, at[1] - 4.5);
      final @NotNull Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.translate((float) corner[0], (float) corner[1]);
      graphics.fill(0, 0, width + 1, 9, 0x80000000);
      graphics.text(font, name, 1, 1, 0xFFFFFFFF, false);
      pose.popMatrix();
    }
  }

  private void drawPoints(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull List<DrawnPoint> points,
      final double[] mouse) {
    final @NotNull Font font = Minecraft.getInstance().font;
    this.labels.clear();
    for (final @NotNull DrawnPoint drawn : points) {
      final double @NotNull [] at = toScreen(drawn.shape().point);
      final @NotNull MarkerShape shape = drawn.shape();
      icon(
          graphics,
          at,
          0,
          drawn.layer().personal()
              ? () -> MapIcons.gem(graphics, shape.color)
              : () ->
                  graphics.blitSprite(RenderPipelines.GUI_TEXTURED, shape.banner(), 0, 0, 8, 8));
      if (hovers(at, mouse[0], mouse[1])) {
        hovered(drawn.layer(), drawn.marker(), drawn.shape());
      }
    }
    if (this.minimap) {
      return;
    }
    for (final @NotNull DrawnPoint drawn : points) {
      final String label = drawn.marker().label();
      if (label == null || label.isEmpty()) {
        continue;
      }
      final double @NotNull [] at = toScreen(drawn.shape().point);
      final int width = font.width(label);
      final double @NotNull [] corner = snapped(at[0] - (width + 1) / 2.0, at[1] + 5);
      final double @NotNull [] box = {corner[0], corner[1], corner[0] + width + 1, corner[1] + 9};
      if (this.labels.stream()
          .anyMatch(
              other ->
                  box[0] < other[2] + LABEL_GAP
                      && other[0] < box[2] + LABEL_GAP
                      && box[1] < other[3] + LABEL_GAP
                      && other[1] < box[3] + LABEL_GAP)) {
        continue;
      }
      this.labels.add(box);
      final @NotNull Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.translate((float) corner[0], (float) corner[1]);
      graphics.fill(0, 0, width + 1, 9, 0x80000000);
      graphics.text(font, label, 1, 1, 0xFFFFFFFF, false);
      pose.popMatrix();
    }
  }

  private void drawTarget(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final int x0,
      final int y0,
      final int x1,
      final int y1) {
    final Navigation.@Nullable Target target = TaerraClient.navigation().target();
    final double @Nullable [] at =
        target == null || this.world == null ? null : target.in(this.world);
    if (at == null) {
      return;
    }
    final double @NotNull [] point =
        toScreen(
            Double.isNaN(at[1])
                ? ground(map, projection, at[0], at[2])
                : projection.toMap(at[0], at[1], at[2]));
    final double @NotNull [] shown = onScreen(point);
    final boolean inside = shown[0] >= x0 && shown[0] < x1 && shown[1] >= y0 && shown[1] < y1;
    if (!inside && !this.minimap) {
      return;
    }
    final @NotNull TextureAtlasSprite sprite =
        MapIcons.decoration(inside ? "target_x" : "target_point");
    icon(
        graphics,
        inside ? point : offMap(shown, x0, y0, x1, y1),
        0,
        () ->
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 8, 8, target.color()));
  }

  private void drawPlayers(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull WebMap map,
      final @NotNull Projection projection,
      final int x0,
      final int y0,
      final int x1,
      final int y1,
      final double[] mouse,
      final float partialTick) {
    if (!map.shown(Layer.PLAYERS)) {
      return;
    }
    final @Nullable LocalPlayer self = Minecraft.getInstance().player;
    final String selfId = self == null ? "" : self.getUUID().toString();
    final @NotNull TextureAtlasSprite arrow = MapIcons.decoration("player");
    for (final @NotNull Player player : map.players()) {
      if (!player.world().equals(this.world) || player.uuid().equals(selfId)) {
        continue;
      }
      final double[] position = map.position(player);
      final double @NotNull [] at =
          toScreen(projection.toMap(position[0], position[1], position[2]));
      final int color = (player.hidden() ? 0x80000000 : 0xFF000000) | Colors.argb(player.color());
      final double @NotNull [] shown = onScreen(at);
      if (shown[0] < x0 || shown[0] >= x1 || shown[1] < y0 || shown[1] >= y1) {
        if (this.minimap) {
          final double @NotNull [] pin = offMap(shown, x0, y0, x1, y1);
          final @NotNull TextureAtlasSprite offMap = MapIcons.decoration("player_off_map");
          icon(
              graphics,
              pin,
              projection.bearing(player.yaw()) + this.spin,
              () -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, offMap, 0, 0, 8, 8, color));
        }
        continue;
      }
      final @Nullable PlayerInfo info = player.anonymous() ? null : PlayerInfos.find(player.uuid());
      if (info != null && this.zoom >= this.maxZoom) {
        icon(
            graphics,
            at,
            0,
            () -> {
              graphics.fill(-1, -1, 9, 9, 0xFF000000);
              PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), 0, 0, 8);
            });
      } else {
        icon(
            graphics,
            at,
            projection.bearing(player.yaw()) + this.spin,
            () -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, arrow, 0, 0, 8, 8, color));
      }
      if (hovers(at, mouse[0], mouse[1])) {
        final @NotNull List<Component> lines = new ArrayList<>();
        lines.add(player.displayName());
        lines.add(player.coordinates().copy().withStyle(ChatFormatting.GRAY));
        if (player.hidden()) {
          lines.add(Component.translatable("taerra.map.hidden").withStyle(ChatFormatting.GRAY));
        }
        this.tooltip = lines;
        this.hover = null;
      }
    }
    if (self != null && Dimensions.current().equals(this.world)) {
      final @NotNull Vec3 position = self.getPosition(partialTick);
      icon(
          graphics,
          toScreen(projection.toMap(position.x, position.y, position.z)),
          projection.bearing(self.getViewYRot(partialTick)) + this.spin,
          () -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, arrow, 0, 0, 8, 8));
    }
  }

  private record DrawnPoint(Layer layer, Marker marker, MarkerShape shape) {}
}
