package net.aerulion.taerra.mod.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.aerulion.taerra.mod.TaerraClient;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.gui.widget.CompassButton;
import net.aerulion.taerra.mod.gui.widget.IconButton;
import net.aerulion.taerra.mod.gui.widget.LayerList;
import net.aerulion.taerra.mod.gui.widget.PanelList;
import net.aerulion.taerra.mod.gui.widget.PlaceMenu;
import net.aerulion.taerra.mod.gui.widget.PlayerList;
import net.aerulion.taerra.mod.gui.widget.RenderProgressOverlay;
import net.aerulion.taerra.mod.gui.widget.SkyButton;
import net.aerulion.taerra.mod.gui.widget.WorldPicker;
import net.aerulion.taerra.mod.input.TaerraKeys;
import net.aerulion.taerra.mod.map.ChunkSelection;
import net.aerulion.taerra.mod.map.Drawing;
import net.aerulion.taerra.mod.map.MapView;
import net.aerulion.taerra.mod.map.MarkerHover;
import net.aerulion.taerra.mod.map.Place;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.Toasts;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapListener;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.Marker;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.Progress;
import net.aerulion.taerra.mod.web.model.Regions;
import net.aerulion.taerra.mod.web.model.Waypoint;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapScreen extends Screen {

  private static final Identifier LIST_BACKGROUND =
      Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
  private static final int BUTTON_WIDTH = 74;
  private static final int REGION_CHUNKS = 65536;
  private static final int PANEL_BUTTON_WIDTH = 74;
  private static final int SPAWN_BUTTON_WIDTH = 50;
  private static final int TITLE_GAP = 6;
  private static final int MIN_TITLE = 24;
  private static final int SCOPE_HEIGHT = 24;
  private static final double CLICK_SLOP = 3;
  private static final int COMPASS_MARGIN = 6;
  private static final int NEAR_ZOOM = 2;
  private static final int TOP_DOWN = -1;
  private static final long STATUS_MILLIS = 4000;
  private static final long CURSOR_HOLD_MILLIS = 500;
  private static final int MIN_SELECTION_BUTTON = 40;
  private static final List<Integer> BRUSH_SIZES = List.of(1, 3, 5);
  private static final int HISTORY = 200;
  private static final List<String> REGION_PALETTE =
      List.of(
          "#3ab3da", "#b02e26", "#80c71f", "#f9801d", "#8932b8", "#fed83d", "#169c9c", "#c74ebd",
          "#3c44aa", "#5e7c16", "#f38baa", "#835432");

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final @NotNull WebMap map;
  private final @NotNull MapView view;
  private final Settings settings;
  private final @NotNull PlayerList players;
  private final @NotNull Map<Long, Regions.Region> regionOwners = new HashMap<>();
  private @Nullable Regions indexedRegions;
  private final @NotNull LayerList layers;
  private final @NotNull WorldPicker worldPicker;
  private final WebMapListener listener =
      new WebMapListener() {
        @Override
        public void worldsChanged() {
          rebuildWidgets();
        }

        @Override
        public void playersChanged() {
          refreshPlayers();
        }

        @Override
        public void markersChanged(final @NotNull String world) {
          if (world.equals(MapScreen.this.view.world())) {
            MapScreen.this.layers.refresh(world);
          }
        }
      };
  private @Nullable Button worldButton;
  private @Nullable Button playersButton;
  private @Nullable CycleButton<Boolean> scope;
  private @Nullable CompassButton compass;
  private @Nullable SkyButton sky;
  private @Nullable IconButton zoomIn;
  private @Nullable IconButton zoomOut;
  private boolean playersOpen;
  private boolean layersOpen;
  private boolean dragging;
  private double dragDistance;
  private double dragVelocityX;
  private double dragVelocityY;
  private long lastDrag;
  private @Nullable PlaceMenu menu;
  private @Nullable String followed;
  private @Nullable Waypoint moving;
  private final List<double[]> lasso = new ArrayList<>();
  private final List<double[]> path = new ArrayList<>();
  private @Nullable Waypoint editing;
  private @NotNull Kind kind = Kind.CHUNKS;
  private @NotNull SelectionTool lastTool = SelectionTool.BRUSH;
  private boolean smooth;
  private @Nullable Button saveButton;
  private @Nullable IconButton undoButton;
  private @Nullable IconButton redoButton;
  private @Nullable String editingId;
  private boolean deleteArmed;
  private @Nullable String regionId;
  private @NotNull String regionName = "";
  private @NotNull String regionFog = "hide";
  private boolean regionDiscoverable = true;
  private @Nullable Regions.Region region;
  private int brushSize = 1;
  private boolean eraseMode;
  private final Deque<Edit> history = new ArrayDeque<>();
  private final Deque<Edit> future = new ArrayDeque<>();
  private @Nullable List<double[]> pathBefore;
  private @NotNull List<Long> hover = List.of();
  private boolean hoverErase;
  private @Nullable Regions othersOf;
  private @Nullable String othersWithout;
  private @NotNull List<Drawing.Area> others = List.of();
  private @NotNull String color = Waypoint.DEFAULT_COLOR;
  private int vertex = -1;
  private boolean vertexExisting;
  private boolean vertexMoved;
  private @Nullable ChunkSelection selection;
  private @NotNull SelectionTool tool = SelectionTool.BRUSH;
  private boolean stroking;
  private boolean erasing;
  private @Nullable Place anchor;
  private @Nullable Place reach;
  private @Nullable Place cursor;
  private long cursorAt;
  private @Nullable Component selectionStatus;
  private boolean selectionFailed;
  private long selectionStatusUntil;
  private boolean sending;
  private int dragButton;
  private int footerWidth;
  private @Nullable Component notice;
  private long noticeUntil;

  public MapScreen(
      final @NotNull WebMap map, final @NotNull MapView view, final Settings settings) {
    super(title(map.config()));
    this.map = map;
    this.view = view;
    this.settings = settings;
    this.players = new PlayerList(map, settings, view::world, this::worldName, this::follow);
    this.layers = new LayerList(map);
    this.worldPicker = new WorldPicker(map::worlds, view::world, map::sky, this::pickWorld);
    final @Nullable MapConfig config = map.config();
    final @NotNull String current = Dimensions.current();
    final @NotNull Minecraft minecraft = Minecraft.getInstance();
    if (config != null && map.world(current) != null && minecraft.player != null) {
      final @NotNull Vec3 position = minecraft.player.position();
      view.setWorld(current);
      view.centerOn(config, position.x, position.y, position.z);
    }
  }

  private static @NotNull Component viewName(final int view) {
    return Component.translatable(
        view == TOP_DOWN ? "taerra.map.view.top" : "taerra.map.view.isometric." + view);
  }

  private static @NotNull Component viewTooltip() {
    return Component.translatable(
        "taerra.map.view.tooltip",
        TaerraKeys.TOP_DOWN.getTranslatedKeyMessage(),
        TaerraKeys.ISOMETRIC.getTranslatedKeyMessage(),
        TaerraKeys.ROTATE.getTranslatedKeyMessage(),
        TaerraKeys.COLORS.getTranslatedKeyMessage(),
        TaerraKeys.PANELS.getTranslatedKeyMessage());
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    keepFollowing(partialTick);
    final int top = mapTop();
    final int bottom = mapBottom();
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        LIST_BACKGROUND,
        0,
        top,
        0,
        0,
        this.width,
        bottom - top,
        32,
        32);
    final boolean hovering = overMap(mouseX, mouseY);
    updateHover(hovering ? mouseX : -1, mouseY);
    this.view.draw(
        graphics,
        this.map,
        0,
        top,
        this.width,
        bottom,
        hovering ? mouseX : -1,
        hovering ? mouseY : -1,
        partialTick);
    if (this.playersOpen) {
      this.players.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          LIST_BACKGROUND,
          0,
          bottom - SCOPE_HEIGHT,
          0,
          0,
          PanelList.WIDTH,
          SCOPE_HEIGHT,
          32,
          32);
    }
    if (this.layersOpen) {
      this.layers.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        INWORLD_HEADER_SEPARATOR,
        0,
        top - 2,
        0,
        0,
        this.width,
        2,
        32,
        2);
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        INWORLD_FOOTER_SEPARATOR,
        0,
        bottom,
        0,
        0,
        this.width,
        2,
        32,
        2);
    drawOverlay(graphics, mouseX, mouseY, partialTick, hovering);
    final boolean covered =
        (this.menu != null && this.menu.isMouseOver(mouseX, mouseY)) || overWorlds(mouseX, mouseY);
    super.extractRenderState(graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, partialTick);
    if (this.menu != null || this.worldPicker.isOpen()) {
      graphics.nextStratum();
    }
    if (this.menu != null) {
      this.menu.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    if (this.worldPicker.isOpen()) {
      layoutWorlds();
      this.worldPicker.render(graphics, mouseX, mouseY, partialTick);
    }
  }

  @Override
  public boolean keyPressed(final KeyEvent event) {
    if (this.worldPicker.isOpen()) {
      if (event.isEscape()) {
        openWorlds(false);
        return true;
      }
      if (event.key() == InputConstants.KEY_RETURN
          || event.key() == InputConstants.KEY_NUMPADENTER) {
        final @Nullable World first = this.worldPicker.first();
        if (first != null) {
          pickWorld(first);
        }
        return true;
      }
      return super.keyPressed(event);
    }
    if (this.menu != null) {
      if (event.isEscape() || event.isCycleFocus()) {
        closeMenu();
        return true;
      }
      if (this.menu.keyPressed(event)) {
        return true;
      }
    }
    if (this.moving != null && event.isEscape()) {
      this.moving = null;
      return true;
    }
    if (this.selection != null && event.isEscape()) {
      stopSelecting();
      return true;
    }
    if (this.selection != null
        && !(getFocused() instanceof EditBox)
        && event.key() >= InputConstants.KEY_1
        && event.key() < InputConstants.KEY_1 + tools().size()) {
      setTool(tools().get(event.key() - InputConstants.KEY_1));
      return true;
    }
    if (this.selection != null
        && this.kind != Kind.CHUNKS
        && (event.key() == InputConstants.KEY_RETURN
            || event.key() == InputConstants.KEY_NUMPADENTER)
        && getFocused() == null) {
      save();
      return true;
    }
    if (this.selection != null
        && this.tool == SelectionTool.POINTS
        && event.key() == InputConstants.KEY_BACKSPACE
        && !this.path.isEmpty()) {
      remember(new Edit(copyPath(), null));
      this.path.removeLast();
      selectionChanged();
      return true;
    }
    if (this.selection != null
        && !this.stroking
        && event.hasControlDown()
        && (event.key() == InputConstants.KEY_Z || event.key() == InputConstants.KEY_Y)) {
      if (event.key() == InputConstants.KEY_Y || event.hasShiftDown()) {
        redo();
      } else {
        undo();
      }
      return true;
    }
    if (this.selection != null
        && !(getFocused() instanceof EditBox)
        && (event.key() == InputConstants.KEY_LBRACKET
            || event.key() == InputConstants.KEY_RBRACKET)) {
      final int at =
          BRUSH_SIZES.indexOf(this.brushSize)
              + (event.key() == InputConstants.KEY_RBRACKET ? 1 : -1);
      setBrushSize(BRUSH_SIZES.get(Math.clamp(at, 0, BRUSH_SIZES.size() - 1)));
      return true;
    }
    if (this.selection == null && TaerraKeys.SEARCH.matches(event) && !event.hasAltDown()) {
      openSearch();
      return true;
    }
    if (TaerraKeys.OPEN_MAP.matches(event)) {
      onClose();
      return true;
    }
    if (shortcut(event)) {
      return true;
    }
    return super.keyPressed(event);
  }

  private static @NotNull Component title(final @Nullable MapConfig config) {
    return config == null || config.name() == null || config.name().isBlank()
        ? Component.translatable("taerra.map")
        : Component.translatable("taerra.map.named", config.name());
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    this.layout.setFooterHeight(HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    this.footerWidth = this.width;
    final List<World> worlds = this.map.worlds();
    if (this.map.world(this.view.world()) == null && !worlds.isEmpty()) {
      selectWorld(worlds.getFirst());
    }
    final int titleRoom =
        this.width - 2 * (8 + PANEL_BUTTON_WIDTH + 4 + SPAWN_BUTTON_WIDTH + TITLE_GAP);
    if (titleRoom >= MIN_TITLE) {
      this.layout.addToHeader(
          new StringWidget(title(this.map.config()), this.font).setMaxWidth(titleRoom));
    }
    this.playersButton =
        this.layout.addToHeader(
            Button.builder(
                    Component.translatable("taerra.map.players"),
                    _ -> setPanels(!this.playersOpen, this.layersOpen))
                .width(PANEL_BUTTON_WIDTH)
                .build(),
            settings -> settings.align(0, .5f).paddingLeft(8));
    this.layout.addToHeader(
        Button.builder(Component.translatable("taerra.map.search"), _ -> openSearch())
            .tooltip(
                Tooltip.create(
                    Component.translatable(
                        "taerra.map.search.tooltip", TaerraKeys.SEARCH.getTranslatedKeyMessage())))
            .width(SPAWN_BUTTON_WIDTH)
            .build(),
        settings -> settings.align(0, .5f).paddingLeft(8 + PANEL_BUTTON_WIDTH + 4));
    this.layout.addToHeader(
        Button.builder(Component.translatable("taerra.map.spawn"), _ -> goToSpawn())
            .tooltip(Tooltip.create(Component.translatable("taerra.map.spawn.tooltip")))
            .width(SPAWN_BUTTON_WIDTH)
            .build(),
        settings -> settings.align(1, .5f).paddingRight(8 + PANEL_BUTTON_WIDTH + 4));
    this.layout.addToHeader(
        Button.builder(
                Component.translatable("taerra.map.layers"),
                _ -> setPanels(this.playersOpen, !this.layersOpen))
            .width(PANEL_BUTTON_WIDTH)
            .build(),
        settings -> settings.align(1, .5f).paddingRight(8));

    final @NotNull LinearLayout footer =
        this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
    final @Nullable World world = this.map.world(this.view.world());
    this.worldButton = null;
    if (this.selection != null) {
      addSelectionButtons(footer);
    } else {
      final @NotNull List<AbstractWidget> view = new ArrayList<>();
      if (world == null) {
        final @NotNull Button loading =
            Button.builder(Component.translatable("taerra.map.loading"), _ -> {})
                .width(2 * BUTTON_WIDTH + 6)
                .build();
        loading.active = false;
        view.add(loading);
      } else {
        this.worldButton =
            Button.builder(world.label(), _ -> openWorlds(!this.worldPicker.isOpen()))
                .tooltip(Tooltip.create(Component.translatable("taerra.map.world.tooltip")))
                .width(BUTTON_WIDTH)
                .build();
        view.add(this.worldButton);
        view.add(
            Button.builder(
                    viewName(this.view.isometric() ? this.view.rotation() : TOP_DOWN),
                    _ -> {
                      setView(this.view.isometric() ? TOP_DOWN : this.view.rotation());
                      rebuildWidgets();
                    })
                .tooltip(Tooltip.create(viewTooltip()))
                .width(BUTTON_WIDTH)
                .build());
        final @NotNull IconButton left =
            iconButton(
                20,
                IconButton.COUNTER_CLOCKWISE,
                Component.translatable(
                    "taerra.map.rotate.left",
                    Component.literal("Shift+")
                        .append(TaerraKeys.ROTATE.getTranslatedKeyMessage())),
                () -> rotate(1));
        left.active = this.view.isometric();
        view.add(left);
        final @NotNull IconButton right =
            iconButton(
                20,
                IconButton.CLOCKWISE,
                Component.translatable(
                    "taerra.map.rotate.right", TaerraKeys.ROTATE.getTranslatedKeyMessage()),
                () -> rotate(-1));
        right.active = this.view.isometric();
        view.add(right);
      }
      final @NotNull List<AbstractWidget> menu = new ArrayList<>();
      final @Nullable Account account = this.map.account();
      if (account != null && account.personal()) {
        menu.add(
            Button.builder(Component.translatable("taerra.waypoints"), _ -> openWaypointList())
                .tooltip(
                    Tooltip.create(
                        Component.translatable(
                            "taerra.waypoints.tooltip",
                            TaerraKeys.WAYPOINT_LIST.getTranslatedKeyMessage())))
                .width(BUTTON_WIDTH)
                .build());
      }
      if (account != null && account.regions()) {
        menu.add(
            Button.builder(Component.translatable("taerra.regions"), _ -> openRegionList())
                .width(BUTTON_WIDTH)
                .build());
      }
      menu.add(
          Button.builder(
                  Component.translatable("menu.options"),
                  _ ->
                      this.minecraft.setScreenAndShow(
                          new SettingsScreen(this, this.settings, this.map)))
              .width(BUTTON_WIDTH)
              .build());
      menu.add(
          Button.builder(Component.translatable("gui.done"), _ -> onClose())
              .width(BUTTON_WIDTH)
              .build());
      if (wanted(view) + 6 + wanted(menu) <= this.width - 16) {
        view.forEach(footer::addChild);
        menu.forEach(footer::addChild);
      } else {
        addRows(footer, view, menu);
      }
    }

    if (this.worldButton == null && this.worldPicker.isOpen()) {
      this.worldPicker.setOpen(false);
    }
    final @Nullable EditBox search = this.worldPicker.createSearch(this.font, worlds.size());
    if (search != null) {
      addWidget(search);
    }
    addWidget(this.worldPicker.list());
    addWidget(this.players);
    addWidget(this.layers);
    this.scope =
        addRenderableWidget(
            CycleButton.booleanBuilder(
                    Component.translatable("taerra.players.all"),
                    Component.translatable("taerra.players.here"),
                    this.settings.allWorldPlayers())
                .displayOnlyValue()
                .create(
                    0,
                    0,
                    PanelList.WIDTH - 8,
                    20,
                    Component.translatable("taerra.players.scope"),
                    (_, all) -> {
                      this.settings.setAllWorldPlayers(all);
                      this.settings.save();
                      refreshPlayers();
                    }));
    this.compass =
        addWidget(
            new CompassButton(
                () -> {
                  final @Nullable MapConfig config = this.map.config();
                  return config == null
                      ? 0
                      : this.view.projection(config).bearing(180) + this.view.spin();
                },
                () -> rotate(-1)));
    this.sky =
        addWidget(
            new SkyButton(
                () -> {
                  final @Nullable String shown = this.view.world();
                  return shown == null ? null : this.map.sky(shown);
                },
                () -> openWorlds(!this.worldPicker.isOpen())));
    this.zoomIn = addWidget(zoomButton(IconButton.PLUS, "taerra.map.zoom_in", 1));
    this.zoomOut = addWidget(zoomButton(IconButton.MINUS, "taerra.map.zoom_out", -1));
    this.layout.visitWidgets(this::addRenderableWidget);
    if (this.menu != null) {
      addWidget(this.menu);
    }

    this.worldPicker.refresh();
    this.layers.refresh(this.view.world());
    refreshPlayers();
    setPanels(this.playersOpen, this.layersOpen);
    syncCompass();
    if (this.worldPicker.isOpen() && search != null) {
      setFocused(search);
    }
  }

  @Override
  public void tick() {
    if (this.menu != null && this.menu.expired()) {
      closeMenu();
    }
    this.map.focus(this.view.world());
  }

  @Override
  public void removed() {
    this.map.removeListener(this.listener);
    this.map.focus(null);
    super.removed();
  }

  @Override
  public void added() {
    super.added();
    this.map.addListener(this.listener);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void repositionElements() {
    if (this.footerWidth != this.width) {
      rebuildWidgets();
    }
    this.layout.arrangeElements();
    final int height = mapBottom() - mapTop();
    this.players.updateSizeAndPosition(PanelList.WIDTH, height - SCOPE_HEIGHT, 0, mapTop());
    this.layers.updateSizeAndPosition(
        PanelList.WIDTH, height, this.width - PanelList.WIDTH, mapTop());
    if (this.scope != null) {
      this.scope.setPosition(4, mapBottom() - SCOPE_HEIGHT + 2);
    }
    final int controlsX = mapRight() - COMPASS_MARGIN - CompassButton.SIZE;
    if (this.zoomIn != null && this.zoomOut != null) {
      this.zoomIn.setPosition(controlsX, mapTop() + COMPASS_MARGIN);
      this.zoomOut.setPosition(controlsX, mapTop() + COMPASS_MARGIN + CompassButton.SIZE);
    }
    if (this.compass != null) {
      this.compass.setPosition(
          controlsX, mapTop() + COMPASS_MARGIN + 2 * CompassButton.SIZE + COMPASS_MARGIN);
    }
    if (this.sky != null) {
      this.sky.setPosition(
          controlsX, mapTop() + COMPASS_MARGIN + 3 * CompassButton.SIZE + 2 * COMPASS_MARGIN);
    }
  }

  @Override
  public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
    if (this.worldPicker.isOpen()
        && !overWorlds(event.x(), event.y())
        && (this.worldButton == null || !this.worldButton.isMouseOver(event.x(), event.y()))) {
      openWorlds(false);
      return true;
    }
    if (this.menu != null) {
      if (this.menu.isMouseOver(event.x(), event.y())) {
        this.menu.mouseClicked(event, doubleClick);
        return true;
      }
      closeMenu();
      if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !overMap(event.x(), event.y())) {
        return true;
      }
    }
    if (super.mouseClicked(event, doubleClick)) {
      final @Nullable EditBox search = this.worldPicker.search();
      if (this.worldPicker.isOpen() && search != null && getFocused() == this.worldButton) {
        setFocused(search);
      }
      return true;
    }
    if (overCompass(event.x(), event.y())) {
      return true;
    }
    if (!overMap(event.x(), event.y())) {
      return false;
    }
    if (this.selection != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
      setFocused(null);
      final @Nullable Place place = this.view.placeAt(this.map, event.x(), event.y());
      if (place != null && this.tool == SelectionTool.POINTS) {
        int hit = this.view.pathPointAt(event.x(), event.y());
        this.pathBefore = copyPath();
        if (event.hasShiftDown()) {
          if (hit >= 0) {
            remember(new Edit(this.pathBefore, null));
            this.path.remove(hit);
          }
        } else {
          this.vertexExisting = hit >= 0;
          this.vertexMoved = false;
          if (hit < 0) {
            final int segment = this.view.pathSegmentAt(event.x(), event.y());
            hit = segment < 0 ? this.path.size() : segment + 1;
            this.path.add(hit, new double[] {place.x() + .5, place.z() + .5});
          }
          this.vertex = hit;
        }
        selectionChanged();
      } else if (place != null) {
        this.stroking = true;
        this.erasing = this.eraseMode != event.hasShiftDown();
        this.hover = List.of();
        this.selection.begin(this.erasing);
        this.anchor = place;
        this.reach = place;
        this.lasso.clear();
        this.lasso.add(new double[] {place.x() + .5, place.z() + .5});
        if (this.tool == SelectionTool.BRUSH) {
          this.selection.brush(place.x(), place.z(), this.brushSize);
        }
        selectionChanged();
      }
      return true;
    }
    if (this.selection == null && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
      this.moving = null;
      openPlaceMenu(event.x(), event.y());
      return true;
    }
    if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
        || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
      this.dragging = true;
      this.dragButton = event.button();
      this.dragDistance = 0;
      this.dragVelocityX = 0;
      this.dragVelocityY = 0;
      this.lastDrag = System.nanoTime();
      this.view.stop();
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseReleased(final MouseButtonEvent event) {
    if (this.vertex >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
      if (this.vertexExisting && !this.vertexMoved && this.vertex < this.path.size()) {
        this.path.remove(this.vertex);
      }
      if (this.pathBefore != null && !samePath(this.pathBefore, this.path)) {
        remember(new Edit(this.pathBefore, null));
      }
      this.pathBefore = null;
      selectionChanged();
      this.vertex = -1;
      return true;
    }
    if (this.stroking && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
      this.stroking = false;
      final @Nullable ChunkSelection selection = this.selection;
      if (selection != null && this.anchor != null && this.reach != null) {
        if (this.tool == SelectionTool.RECTANGLE) {
          selection.box(this.anchor.x(), this.anchor.z(), this.reach.x(), this.reach.z());
        } else if (this.tool == SelectionTool.LASSO) {
          selection.lasso(this.lasso.toArray(double[][]::new));
        }
      }
      if (selection != null) {
        final ChunkSelection.@Nullable Change change = selection.end();
        if (change != null) {
          remember(new Edit(null, change));
        }
      }
      this.lasso.clear();
      selectionChanged();
      return true;
    }
    if (this.dragging && event.button() == this.dragButton) {
      this.dragging = false;
      if (this.dragDistance <= CLICK_SLOP) {
        final @Nullable MarkerHover hover = this.view.hover();
        if (this.selection != null) {
          return true;
        }
        if (this.moving != null) {
          moveWaypoint(event.x(), event.y());
        } else if (hover != null) {
          openMarkerMenu(hover, event.x(), event.y());
        }
      } else if (System.nanoTime() - this.lastDrag < 50_000_000L) {
        this.view.glide(this.dragVelocityX, this.dragVelocityY);
      }
      return true;
    }
    return super.mouseReleased(event);
  }

  @Override
  public boolean mouseDragged(
      final MouseButtonEvent event, final double dragX, final double dragY) {
    if (this.vertex >= 0) {
      final @Nullable Place place =
          overMap(event.x(), event.y()) ? this.view.placeAt(this.map, event.x(), event.y()) : null;
      if (place != null && this.vertex < this.path.size()) {
        final double @NotNull [] moved = {place.x() + .5, place.z() + .5};
        if (!Arrays.equals(moved, this.path.get(this.vertex))) {
          this.path.set(this.vertex, moved);
          this.vertexMoved = true;
          selectionChanged();
        }
      }
      return true;
    }
    if (this.stroking) {
      final int steps =
          this.tool == SelectionTool.BRUSH ? (int) Math.ceil(Math.hypot(dragX, dragY) / 2) : 1;
      for (int step = 1; step <= steps; step++) {
        stroke(event.x() - dragX + dragX * step / steps, event.y() - dragY + dragY * step / steps);
      }
      selectionChanged();
      return true;
    }
    if (!this.dragging) {
      return super.mouseDragged(event, dragX, dragY);
    }
    this.dragDistance += Math.hypot(dragX, dragY);
    if (this.dragDistance > CLICK_SLOP) {
      setFollowed(null);
    }
    this.view.pan(dragX, dragY);
    final long now = System.nanoTime();
    final double seconds = Math.max((now - this.lastDrag) / 1e9, 1e-3);
    this.lastDrag = now;
    this.dragVelocityX = this.dragVelocityX * .5 + dragX / seconds * .5;
    this.dragVelocityY = this.dragVelocityY * .5 + dragY / seconds * .5;
    return true;
  }

  @Override
  public boolean mouseScrolled(
      final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
    if (this.worldButton != null && this.worldButton.isMouseOver(mouseX, mouseY) && scrollY != 0) {
      cycleWorld(scrollY > 0 ? -1 : 1);
      return true;
    }
    if (overMap(mouseX, mouseY) && scrollY != 0) {
      closeMenu();
      this.view.zoomAt(Math.signum(scrollY), mouseX, mouseY);
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  private @NotNull Component worldName(final String name) {
    final @Nullable World world = this.map.world(name);
    return Component.literal(world == null ? name : world.displayName());
  }

  private void refreshPlayers() {
    this.players.refresh();
    if (this.playersButton != null) {
      this.playersButton.setMessage(
          Component.translatable("taerra.map.players.count", this.players.count()));
    }
  }

  private void setPanels(final boolean players, final boolean layers) {
    this.playersOpen = players;
    this.layersOpen = layers;
    this.players.visible = players;
    this.layers.visible = layers;
    if (this.scope != null) {
      this.scope.visible = players;
    }
    repositionElements();
  }

  private void syncCompass() {
    final @Nullable CompassButton compass = this.compass;
    if (compass == null) {
      return;
    }
    compass.visible = this.map.config() != null && this.map.world(this.view.world()) != null;
    if (this.zoomIn != null && this.zoomOut != null) {
      this.zoomIn.visible = compass.visible;
      this.zoomOut.visible = compass.visible;
    }
    compass.active = this.view.isometric();
    compass.setTooltip(
        Tooltip.create(
            Component.translatable(
                this.view.isometric() ? "taerra.map.compass.rotate" : "taerra.map.compass")));
  }

  private void setFollowed(final @Nullable String uuid) {
    this.followed = uuid;
    this.players.select(uuid);
  }

  private void zoomNear() {
    this.view.zoomToward(Math.max(this.view.targetZoom(), NEAR_ZOOM));
  }

  private void selectWorld(final @NotNull World world) {
    final @Nullable MapConfig config = this.map.config();
    switchWorld(world.name());
    if (config != null) {
      this.view.centerOn(config, world.spawn().x(), world.spawn().y(), world.spawn().z());
      zoomNear();
    }
  }

  private void switchWorld(final @NotNull String world) {
    this.view.setWorld(world);
    this.moving = null;
    this.selection = null;
    this.stroking = false;
    this.path.clear();
    this.editing = null;
    this.view.select(null);
  }

  private void openSearch() {
    closeMenu();
    this.minecraft.setScreenAndShow(
        new SearchScreen(this, this.map, this.view.world(), this::jump));
  }

  void jump(final SearchScreen.@NotNull Target target) {
    final @Nullable MapConfig config = this.map.config();
    final @Nullable World world = this.map.world(target.world());
    if (config == null || world == null) {
      return;
    }
    setFollowed(null);
    final @Nullable Player player =
        target.player() == null ? null : this.map.player(target.player());
    if (player != null && player.located()) {
      follow(player);
      return;
    }
    if (!world.name().equals(this.view.world())) {
      selectWorld(world);
    }
    this.view.centerOn(config, target.x(), target.y() == null ? 64 : target.y(), target.z());
    zoomNear();
  }

  private void goToSpawn() {
    final @Nullable World world = this.map.world(this.view.world());
    final @Nullable MapConfig config = this.map.config();
    if (world != null && config != null) {
      setFollowed(null);
      this.view.centerOn(config, world.spawn().x(), world.spawn().y(), world.spawn().z());
      zoomNear();
    }
  }

  private void follow(final @NotNull Player player) {
    if (!player.located()) {
      return;
    }
    if (player.uuid().equals(this.followed)) {
      setFollowed(null);
      return;
    }
    setFollowed(player.uuid());
    closeMenu();
    zoomNear();
  }

  private void keepFollowing(final float partialTick) {
    final @Nullable MapConfig config = this.map.config();
    if (this.followed == null || config == null) {
      return;
    }
    final @Nullable Player player = this.map.player(this.followed);
    final @Nullable World world =
        player == null || !player.located() ? null : this.map.world(player.world());
    if (world == null) {
      setFollowed(null);
      return;
    }
    if (!world.name().equals(this.view.world())) {
      switchWorld(world.name());
      rebuildWidgets();
    }
    if (this.minecraft.player != null
        && this.minecraft.player.getUUID().toString().equals(player.uuid())
        && Dimensions.current().equals(world.name())) {
      final @NotNull Vec3 position = this.minecraft.player.getPosition(partialTick);
      this.view.centerOn(config, position.x, position.y, position.z);
      return;
    }
    final double[] position = this.map.position(player);
    this.view.centerOn(config, position[0], position[1], position[2]);
  }

  private void openWorlds(final boolean open) {
    this.worldPicker.setOpen(open && this.worldButton != null);
    closeMenu();
    if (this.worldPicker.search() != null) {
      setFocused(this.worldPicker.isOpen() ? this.worldPicker.search() : null);
    }
  }

  private void pickWorld(final @NotNull World world) {
    openWorlds(false);
    if (!world.name().equals(this.view.world())) {
      setFollowed(null);
      selectWorld(world);
      rebuildWidgets();
    }
  }

  private void cycleWorld(final int step) {
    final List<World> worlds = this.map.worlds();
    final int index = worlds.indexOf(this.map.world(this.view.world()));
    if (index >= 0) {
      pickWorld(worlds.get(Math.floorMod(index + step, worlds.size())));
    }
  }

  private void layoutWorlds() {
    this.worldPicker.layout(
        this.font,
        this.worldButton == null ? 4 : this.worldButton.getX(),
        this.width,
        mapTop(),
        mapBottom());
  }

  private @Nullable Place cursorPlace(final int mouseX, final int mouseY) {
    final @Nullable Place place = this.view.placeAt(this.map, mouseX, mouseY);
    final long now = System.currentTimeMillis();
    if (place != null && place.y() != null) {
      this.cursor = place;
      this.cursorAt = now;
      return place;
    }
    return this.cursor != null && now - this.cursorAt < CURSOR_HOLD_MILLIS ? this.cursor : place;
  }

  private boolean overWorlds(final double x, final double y) {
    if (!this.worldPicker.isOpen()) {
      return false;
    }
    layoutWorlds();
    return this.worldPicker.contains(x, y);
  }

  private void setView(final int selected) {
    final @Nullable MapConfig config = this.map.config();
    if (config != null) {
      closeMenu();
      this.view.setView(config, selected != TOP_DOWN, Math.max(selected, 0));
      syncCompass();
    }
  }

  private void rotate(final int turn) {
    setView(Math.floorMod(this.view.rotation() + turn, 4));
    rebuildWidgets();
  }

  private boolean shortcut(final @NotNull KeyEvent event) {
    if (event.hasControlDown() || event.hasAltDown() || this.map.config() == null) {
      return false;
    }
    if (TaerraKeys.TOP_DOWN.matches(event)) {
      setView(TOP_DOWN);
    } else if (TaerraKeys.ISOMETRIC.matches(event)) {
      setView(this.view.isometric() ? this.view.rotation() : 0);
    } else if (TaerraKeys.ROTATE.matches(event) && this.view.isometric()) {
      setView(Math.floorMod(this.view.rotation() + (event.hasShiftDown() ? 1 : -1), 4));
    } else if (TaerraKeys.COLORS.matches(event)) {
      this.settings.setGrayMap(!this.settings.grayMap());
      this.settings.save();
      return true;
    } else if (TaerraKeys.WAYPOINT_LIST.matches(event)) {
      openWaypointList();
      return true;
    } else if (TaerraKeys.PANELS.matches(event)) {
      final boolean open = !this.playersOpen && !this.layersOpen;
      setPanels(open, open);
      return true;
    } else {
      return false;
    }
    rebuildWidgets();
    return true;
  }

  private int mapTop() {
    return this.layout.getHeaderHeight();
  }

  private int mapBottom() {
    return this.height - this.layout.getFooterHeight() - 2;
  }

  private int mapLeft() {
    return this.playersOpen ? PanelList.WIDTH : 0;
  }

  private int mapRight() {
    return this.width - (this.layersOpen ? PanelList.WIDTH : 0);
  }

  private boolean overMap(final double x, final double y) {
    return y >= mapTop()
        && y < mapBottom()
        && x >= mapLeft()
        && x < mapRight()
        && (this.menu == null || !this.menu.isMouseOver(x, y))
        && !overCompass(x, y)
        && !overWorlds(x, y);
  }

  private boolean overCompass(final double x, final double y) {
    return over(this.compass, x, y)
        || over(this.zoomIn, x, y)
        || over(this.zoomOut, x, y)
        || over(this.sky, x, y);
  }

  private static boolean over(
      final @Nullable AbstractWidget control, final double x, final double y) {
    return control != null
        && control.visible
        && x >= control.getX()
        && x < control.getRight()
        && y >= control.getY()
        && y < control.getBottom();
  }

  private @NotNull IconButton zoomButton(
      final String @NotNull [] icon, final @NotNull String tooltip, final int steps) {
    return iconButton(
        CompassButton.SIZE,
        icon,
        Component.translatable(tooltip),
        () -> {
          closeMenu();
          this.view.zoomAt(steps, this.width / 2.0, (mapTop() + mapBottom()) / 2.0);
        });
  }

  private static @NotNull IconButton iconButton(
      final int size,
      final String @NotNull [] icon,
      final @NotNull Component tooltip,
      final @NotNull Runnable onPress) {
    final @NotNull IconButton button = new IconButton(size, icon, tooltip, onPress);
    button.setTooltip(Tooltip.create(tooltip));
    return button;
  }

  private void showMenu(final @NotNull PlaceMenu menu, final double mouseX, final double mouseY) {
    closeMenu();
    menu.place(this.font, mouseX, mouseY, mapLeft(), mapTop(), mapRight(), mapBottom());
    this.menu = addWidget(menu);
    setFocused(menu);
  }

  private void closeMenu() {
    final @Nullable PlaceMenu menu = this.menu;
    if (menu == null) {
      return;
    }
    this.menu = null;
    if (getFocused() == menu) {
      setFocused(null);
    }
    removeWidget(menu);
  }

  private void notice(final Component notice) {
    this.notice = notice;
    this.noticeUntil = System.currentTimeMillis() + 3000;
  }

  private void drawOverlay(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick,
      final boolean hovering) {
    final @Nullable MapConfig config = this.map.config();
    final int top = mapTop();
    final int bottom = mapBottom();
    final int left = mapLeft();
    final int right = mapRight();
    final int middle = (left + right) / 2;
    if (this.map.unreachable()) {
      final int center = (top + bottom) / 2;
      graphics.centeredText(
          this.font, Component.translatable("taerra.unreachable"), middle, center - 16, 0xFFFFFFFF);
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.unreachable.detail", this.map.base()),
          middle,
          center,
          0xFFA0A0A0);
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.unreachable.hint"),
          middle,
          center + 12,
          0xFFA0A0A0);
    } else if (this.map.world(this.view.world()) == null || config == null) {
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.map.loading"),
          middle,
          (top + bottom) / 2,
          0xFFA0A0A0);
    } else {
      RenderProgressOverlay.draw(
          graphics, this.font, this.map.progress(), this::worldName, middle, top);
      if (hovering) {
        final @Nullable Place place = cursorPlace(mouseX, mouseY);
        if (place != null) {
          graphics.text(
              this.font,
              Component.translatable(
                  "taerra.map.cursor", place.x(), place.y() == null ? "—" : place.y(), place.z()),
              left + 6,
              bottom - 12,
              0xFFFFFFFF,
              true);
        }
      }
    }
    final boolean covered = this.menu != null && this.menu.isMouseOver(mouseX, mouseY);
    if (this.sky != null) {
      this.sky.sync();
      this.sky.visible &= this.compass != null && this.compass.visible;
    }
    for (final @Nullable AbstractWidget control :
        new AbstractWidget[] {this.zoomIn, this.zoomOut, this.compass, this.sky}) {
      if (control != null && control.visible) {
        control.extractRenderState(
            graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, partialTick);
      }
    }
    if (this.moving != null) {
      drawHints(
          graphics,
          hints(
              "click",
              Component.translatable("taerra.hint.do.move_waypoint", this.moving.name()),
              "esc",
              Component.translatable("taerra.hint.do.cancel")),
          middle,
          bottom - 24,
          right - left);
    } else if (this.selection != null) {
      drawHints(graphics, selectionHints(), middle, bottom - 36, right - left);
      final boolean status =
          this.selectionStatus != null && System.currentTimeMillis() < this.selectionStatusUntil;
      final boolean split =
          this.kind == Kind.AREA
                  && this.tool != SelectionTool.POINTS
                  && this.selection.rings().size() > 1
              || this.kind == Kind.REGION && !this.selection.connected();
      final boolean full =
          this.tool != SelectionTool.POINTS && this.selection.size() >= this.selection.limit();
      graphics.centeredText(
          this.font,
          split
              ? Component.translatable(
                  this.kind == Kind.REGION ? "taerra.region.split" : "taerra.selection.split")
              : status
                  ? this.selectionStatus
                  : full
                      ? Component.translatable("taerra.selection.full", this.selection.limit())
                      : this.tool == SelectionTool.POINTS
                          ? Component.translatable("taerra.selection.points", this.path.size())
                          : this.selection.isEmpty()
                              ? Component.translatable("taerra.selection.empty")
                              : Component.translatable(
                                  this.selection.size() == 1
                                      ? "taerra.selection.count.one"
                                      : "taerra.selection.count",
                                  this.selection.size()),
          middle,
          bottom - 24,
          split || full && !status || status && this.selectionFailed
              ? 0xFFFF5555
              : status ? 0xFF55FF55 : 0xFFFFFF55);
    } else if (this.notice != null && System.currentTimeMillis() < this.noticeUntil) {
      graphics.centeredText(this.font, this.notice, middle, bottom - 24, 0xFFFF5555);
    }
    if (config != null && !this.map.unreachable() && !this.map.live()) {
      final @NotNull Component reconnecting = Component.translatable("taerra.map.reconnecting");
      graphics.text(
          this.font,
          reconnecting,
          right - 6 - this.font.width(reconnecting),
          bottom - 12,
          0xFFA0A0A0,
          true);
    }
  }

  private static @NotNull Component hints(final Object... pairs) {
    final @NotNull MutableComponent hints = Component.empty();
    for (int i = 0; i < pairs.length; i += 2) {
      if (i > 0) {
        hints.append("   ");
      }
      hints
          .append(
              Component.translatable("taerra.hint.key." + pairs[i])
                  .withStyle(ChatFormatting.YELLOW))
          .append(" ")
          .append(((Component) pairs[i + 1]).copy().withStyle(ChatFormatting.GRAY));
    }
    return hints;
  }

  private static @NotNull Component action(final @NotNull String action) {
    return Component.translatable("taerra.hint.do." + action);
  }

  private @NotNull Component selectionHints() {
    final @NotNull Component exit = action(this.kind == Kind.CHUNKS ? "exit" : "cancel");
    return this.tool == SelectionTool.POINTS
        ? hints(
            "click", action("add"),
            "drag", action("move"),
            "click_point", action("remove"),
            "backspace", action("undo"),
            "right_drag", action("pan"),
            "esc", exit)
        : hints(
            "drag", action("paint"),
            "shift", action("erase"),
            "right_drag", action("pan"),
            "esc", exit);
  }

  private void drawHints(
      final @NotNull GuiGraphicsExtractor graphics,
      final @NotNull Component hints,
      final int middle,
      final int bottom,
      final int width) {
    final @NotNull List<FormattedCharSequence> lines = this.font.split(hints, width - 16);
    for (int i = 0; i < lines.size(); i++) {
      graphics.centeredText(
          this.font, lines.get(i), middle, bottom - (lines.size() - 1 - i) * 10, 0xFFFFFFFF);
    }
  }

  private void openPlaceMenu(final double mouseX, final double mouseY) {
    closeMenu();
    final @Nullable MapConfig config = this.map.config();
    final @Nullable String world = this.view.world();
    final @Nullable Place place = this.view.placeAt(this.map, mouseX, mouseY);
    if (config == null || world == null || place == null) {
      return;
    }
    final @Nullable Integer standing = place.standing();
    final @Nullable Account account = this.map.account();
    final @NotNull PlaceMenu menu = new PlaceMenu(place.describe());
    menu.add(
        Component.translatable("taerra.menu.coordinates"),
        item -> copy(menu, item, place.coordinates()));
    if (account != null && account.teleport()) {
      menu.add(
          Component.translatable("taerra.menu.teleport"),
          item -> teleport(menu, item, world, place.x(), standing, place.z()));
    }
    final boolean placing = account != null && (account.markers() || account.personal());
    if (placing) {
      menu.add(
          Component.translatable("taerra.menu.waypoint"),
          _ ->
              this.minecraft.setScreenAndShow(
                  new WaypointScreen(
                      this,
                      this.map,
                      Waypoint.create(world, place.x(), standing, place.z()),
                      () -> {})));
    }
    if (placing) {
      menu.add(
          Component.translatable("taerra.menu.area"),
          _ -> {
            closeMenu();
            startDrawing(Kind.AREA, null, null);
          });
      menu.add(
          Component.translatable("taerra.menu.line"),
          _ -> {
            closeMenu();
            startDrawing(Kind.LINE, null, null);
          });
    }
    if (account != null && account.renderArea()) {
      menu.add(
          Component.translatable("taerra.menu.select"),
          _ -> {
            closeMenu();
            startDrawing(Kind.CHUNKS, null, null);
          });
    }
    if (account != null && account.regions()) {
      final Regions.@Nullable Region owner =
          regionAt(world, Math.floorDiv(place.x(), 16), Math.floorDiv(place.z(), 16));
      menu.add(
          owner == null
              ? Component.translatable("taerra.menu.draw_region")
              : Component.translatable(
                  "taerra.menu.edit_region",
                  owner.name().isEmpty()
                      ? Component.translatable("taerra.regions.unnamed")
                      : Component.literal(owner.name())),
          _ -> {
            closeMenu();
            startRegion(owner);
          });
    }
    final @Nullable Progress progress = this.map.progress();
    final boolean cancel =
        progress != null && progress.render() != null && world.equals(progress.render().world());
    if (account != null && (cancel ? account.cancelRender() : account.renderWorld())) {
      final @NotNull AtomicBoolean armed = new AtomicBoolean();
      menu.add(
          Component.translatable(cancel ? "taerra.menu.cancel_render" : "taerra.menu.render_world"),
          item -> {
            if (!armed.getAndSet(true)) {
              item.show(
                  Component.translatable(
                      cancel ? "taerra.menu.cancel_confirm" : "taerra.menu.render_confirm",
                      worldName(world)),
                  false);
              return;
            }
            renderWorld(menu, item, world, cancel);
          });
    }
    menu.add(
        Component.translatable("taerra.menu.link"),
        item ->
            copy(
                menu,
                item,
                this.map.link(
                    world,
                    place.x(),
                    place.y(),
                    place.z(),
                    this.view.targetZoom(),
                    this.view.isometric(),
                    this.view.rotation())));
    menu.add(
        Component.translatable("taerra.menu.center"),
        _ -> {
          setFollowed(null);
          this.view.centerOn(
              config, place.x() + .5, standing == null ? 64 : standing, place.z() + .5);
          closeMenu();
        });
    menu.add(
        Component.translatable("taerra.menu.navigate"),
        _ ->
            navigate(
                new Navigation.Target(
                    place.describe().getString(),
                    world,
                    place.x() + .5,
                    standing == null ? Double.NaN : standing,
                    place.z() + .5,
                    0xFFFFFFFF,
                    null)));
    final @Nullable Player followed = this.followed == null ? null : this.map.player(this.followed);
    if (followed != null && followed.located()) {
      menu.add(
          Component.translatable("taerra.menu.navigate_player", followed.displayName()),
          _ ->
              navigate(
                  new Navigation.Target(
                      followed.displayName().getString(),
                      followed.world(),
                      followed.x(),
                      followed.y(),
                      followed.z(),
                      0xFF000000 | Colors.argb(followed.color()),
                      followed.uuid())));
    }
    if (TaerraClient.navigation().target() != null) {
      menu.add(
          Component.translatable("taerra.menu.stop_navigation"),
          _ -> {
            TaerraClient.navigation().stop();
            closeMenu();
          });
    }
    showMenu(menu, mouseX, mouseY);
  }

  private void navigate(final Navigation.@NotNull Target target) {
    TaerraClient.navigation().start(target);
    onClose();
  }

  private void openMarkerMenu(
      final @NotNull MarkerHover hover, final double mouseX, final double mouseY) {
    final Marker marker = hover.marker();
    final @Nullable Account account = this.map.account();
    final @Nullable String world = this.view.world();
    final boolean point = "point".equals(marker.type()) && marker.position() != null;
    if (world == null
        || marker.label() == null
        || (!point && (!hover.layer().waypoints() || marker.points() == null))) {
      return;
    }
    final @NotNull Waypoint waypoint = Waypoint.of(world, hover.layer(), marker);
    final @NotNull PlaceMenu menu = new PlaceMenu(Component.literal(marker.label()));
    if (point) {
      menu.add(
          Component.translatable("taerra.waypoint.navigate"),
          _ ->
              navigate(
                  new Navigation.Target(
                      marker.label(),
                      world,
                      marker.position().get(0),
                      marker.position().get(1),
                      marker.position().get(2),
                      0xFF000000 | Colors.argb(marker.color()),
                      null)));
    }
    if (account == null || !hover.layer().waypoints()) {
      showMenu(menu, mouseX, mouseY);
      return;
    }
    if (point && account.teleport()) {
      menu.add(
          Component.translatable("taerra.waypoint.teleport"),
          item -> teleport(menu, item, world, waypoint.x(), waypoint.y(), waypoint.z()));
    }
    if (waypoint.personal()
        ? account.personal()
        : marker.id().startsWith("waypoint:") && account.markers()) {
      if (point) {
        menu.add(
            Component.translatable("taerra.waypoint.move"),
            _ -> {
              closeMenu();
              this.moving = waypoint;
            });
      }
      menu.add(
          Component.translatable("taerra.waypoint.edit_short"),
          _ -> {
            closeMenu();
            editWaypoint(waypoint);
          });
    }
    if (!menu.isEmpty()) {
      showMenu(menu, mouseX, mouseY);
    }
  }

  void editWaypoint(final @NotNull Waypoint waypoint) {
    if ("point".equals(waypoint.type())) {
      this.minecraft.setScreenAndShow(new WaypointScreen(this, this.map, waypoint, () -> {}));
      return;
    }
    final @Nullable World world = this.map.world(waypoint.world());
    if (world != null && !world.name().equals(this.view.world())) {
      selectWorld(world);
    }
    startDrawing(
        waypoint.type().equals("line") ? Kind.LINE : Kind.AREA,
        waypoint,
        (waypoint.personal() ? "personal:" : "waypoint:")
            + waypoint.name().toLowerCase(java.util.Locale.ROOT));
  }

  public void editRegion(final Regions.@Nullable Region region) {
    closeMenu();
    startRegion(region);
  }

  public void openRegionList() {
    final @Nullable Account account = this.map.account();
    final @Nullable String world = this.view.world();
    if (account != null && account.regions() && world != null) {
      closeMenu();
      this.minecraft.setScreenAndShow(new RegionListScreen(this, this.map, world));
    }
  }

  public void openWaypointList() {
    final @Nullable Account account = this.map.account();
    if (account != null && account.personal()) {
      closeMenu();
      this.minecraft.setScreenAndShow(new WaypointListScreen(this, this.map));
    }
  }

  private Regions.@Nullable Region regionAt(
      final @NotNull String world, final int chunkX, final int chunkZ) {
    final @NotNull Regions regions = this.map.regions(world);
    if (regions != this.indexedRegions) {
      this.indexedRegions = regions;
      this.regionOwners.clear();
      for (final Regions.@NotNull Region region :
          regions.regions() == null ? List.<Regions.Region>of() : regions.regions()) {
        for (final int @NotNull [] chunk :
            region.chunks() == null ? List.<int[]>of() : region.chunks()) {
          this.regionOwners.put(ChunkSelection.key(chunk[0], chunk[1]), region);
        }
      }
    }
    return this.regionOwners.get(ChunkSelection.key(chunkX, chunkZ));
  }

  private void startRegion(final Regions.@Nullable Region region) {
    final @Nullable String world = this.view.world();
    if (world == null) {
      return;
    }
    this.region = region;
    this.regionId = region == null ? null : region.id();
    this.regionName = region == null ? "" : region.name();
    this.regionFog = region == null || region.fog() == null ? "hide" : region.fog();
    this.regionDiscoverable = region == null || !Boolean.FALSE.equals(region.discoverable());
    startDrawing(Kind.REGION, null, region == null ? null : "region:" + region.id());
    this.color =
        region != null && region.color() != null && Colors.isHex(region.color())
            ? region.color()
            : freshColor(this.map.regions(world));
    final @Nullable ChunkSelection selection = this.selection;
    if (region != null && region.chunks() != null && selection != null) {
      selection.addAll(
          region.chunks().stream().map(chunk -> ChunkSelection.key(chunk[0], chunk[1])).toList());
    }
    selectionChanged();
  }

  private static @NotNull String freshColor(final @NotNull Regions regions) {
    final @NotNull Map<String, Integer> uses = new HashMap<>();
    for (final Regions.@NotNull Region region :
        regions.regions() == null ? List.<Regions.Region>of() : regions.regions()) {
      if (region.color() != null) {
        uses.merge(region.color(), 1, Integer::sum);
      }
    }
    String best = REGION_PALETTE.getFirst();
    for (final @NotNull String color : REGION_PALETTE) {
      if (uses.getOrDefault(color, 0) < uses.getOrDefault(best, 0)) {
        best = color;
      }
    }
    return best;
  }

  private boolean taken(final long chunk) {
    final @Nullable String world = this.view.world();
    final Regions.@Nullable Region owner =
        world == null
            ? null
            : regionAt(world, ChunkSelection.chunkX(chunk), ChunkSelection.chunkZ(chunk));
    return owner != null && !owner.id().equals(this.regionId);
  }

  private void startDrawing(
      final @NotNull Kind kind,
      final @Nullable Waypoint editing,
      final @Nullable String editingId) {
    this.moving = null;
    this.kind = kind;
    this.editing = editing;
    this.editingId = editingId;
    this.deleteArmed = false;
    this.history.clear();
    this.future.clear();
    this.hover = List.of();
    this.selection =
        kind == Kind.REGION ? new ChunkSelection(REGION_CHUNKS, this::taken) : new ChunkSelection();
    this.path.clear();
    if (editing != null && editing.points() != null) {
      editing.points().forEach(point -> this.path.add(new double[] {point.get(0), point.get(2)}));
    }
    this.smooth = editing != null && editing.smooth();
    this.color = editing == null ? Waypoint.DEFAULT_COLOR : editing.color();
    this.tool = editing != null || kind == Kind.LINE ? SelectionTool.POINTS : this.lastTool;
    selectionChanged();
    rebuildWidgets();
  }

  private void stopSelecting() {
    this.selection = null;
    this.stroking = false;
    this.path.clear();
    this.editing = null;
    this.editingId = null;
    this.saveButton = null;
    this.vertex = -1;
    this.view.select(null);
    rebuildWidgets();
  }

  private void setTool(final @NotNull SelectionTool tool) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null || !tools().contains(tool) || tool == this.tool) {
      return;
    }
    if (this.kind == Kind.AREA
        && (tool == SelectionTool.POINTS) != (this.tool == SelectionTool.POINTS)) {
      if (tool == SelectionTool.POINTS && !selection.isEmpty()) {
        final @NotNull List<int[][]> rings = selection.rings();
        if (rings.size() != 1) {
          showSelectionStatus(Component.translatable("taerra.selection.split"), true);
          return;
        }
        this.path.clear();
        this.path.addAll(ChunkSelection.corners(rings.getFirst()));
        selection.clear();
      } else if (tool != SelectionTool.POINTS && this.path.size() >= 3) {
        selection.begin(false);
        selection.lasso(this.path.toArray(double[][]::new));
        selection.end();
        this.path.clear();
      }
      this.history.clear();
      this.future.clear();
    }
    this.tool = tool;
    if (tool != SelectionTool.POINTS) {
      this.lastTool = tool;
    }
    selectionChanged();
    rebuildWidgets();
  }

  private @NotNull List<SelectionTool> tools() {
    return switch (this.kind) {
      case CHUNKS, REGION ->
          List.of(SelectionTool.BRUSH, SelectionTool.RECTANGLE, SelectionTool.LASSO);
      case AREA -> List.of(SelectionTool.values());
      case LINE -> List.of();
    };
  }

  private @Nullable List<double[]> shape() {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null) {
      return null;
    }
    if (this.kind == Kind.LINE) {
      return this.path.size() >= 2 ? this.path : null;
    }
    if (this.tool == SelectionTool.POINTS) {
      return this.path.size() >= 3 ? this.path : null;
    }
    final @NotNull List<int[][]> rings = selection.rings();
    return rings.size() == 1 ? ChunkSelection.corners(rings.getFirst()) : null;
  }

  private void openRegion() {
    final @Nullable ChunkSelection selection = this.selection;
    final @Nullable String world = this.view.world();
    if (selection == null || world == null || this.sending || !regionReady()) {
      return;
    }
    final @Nullable Regions.Region region = this.region;
    this.minecraft.setScreenAndShow(
        new RegionScreen(
            this,
            this.map,
            new RegionScreen.Draft(
                world,
                this.regionId,
                this.regionName,
                this.color,
                this.regionFog,
                this.regionDiscoverable),
            Set.copyOf(selection.chunks()),
            color -> {
              this.color = color;
              updateDrawing();
            },
            new RegionScreen.Listener() {
              @Override
              public void saved(
                  final @NotNull String id,
                  final RegionScreen.@NotNull Draft draft,
                  final boolean rendering) {
                if (selection != MapScreen.this.selection) {
                  return;
                }
                MapScreen.this.regionId = id;
                MapScreen.this.editingId = "region:" + id;
                MapScreen.this.regionName = draft.name();
                MapScreen.this.regionFog = draft.fog();
                MapScreen.this.regionDiscoverable = draft.discoverable();
                MapScreen.this.color = draft.color();
                selectionChanged();
                showSelectionStatus(
                    Component.translatable(
                        rendering ? "taerra.region.started" : "taerra.region.saved"),
                    false);
                rebuildWidgets();
              }

              @Override
              public void deleted() {
                stopSelecting();
              }
            }));
  }

  private boolean regionReady() {
    final @Nullable ChunkSelection selection = this.selection;
    return selection != null && !selection.isEmpty() && selection.connected();
  }

  private void deleteRegion() {
    final @Nullable String id = this.regionId;
    if (id == null) {
      return;
    }
    if (!this.deleteArmed) {
      this.deleteArmed = true;
      rebuildWidgets();
      return;
    }
    this.map.deleteRegion(
        id,
        (status, body) -> {
          if (status == 200 || status == 202) {
            stopSelecting();
          } else {
            this.deleteArmed = false;
            showSelectionStatus(Component.translatable(RegionScreen.problem(status, body)), true);
            rebuildWidgets();
          }
        });
  }

  private void save() {
    if (this.kind == Kind.REGION) {
      openRegion();
      return;
    }
    final @Nullable List<double[]> shape = shape();
    final @Nullable String world = this.view.world();
    if (shape == null || world == null) {
      return;
    }
    final @NotNull List<List<Double>> points = new ArrayList<>();
    for (final double @NotNull [] point : shape) {
      points.add(Arrays.asList(point[0], heightAt(point[0], point[1]), point[1]));
    }
    final @NotNull Waypoint base =
        this.editing != null ? this.editing : Waypoint.create(world, 0, null, 0);
    this.minecraft.setScreenAndShow(
        new WaypointScreen(
            this,
            this.map,
            base.shaped(
                this.kind == Kind.LINE ? "line" : "area",
                points,
                this.kind == Kind.LINE && this.smooth),
            this::stopSelecting));
  }

  private void delete() {
    final @Nullable Waypoint editing = this.editing;
    final @Nullable String world = this.view.world();
    if (editing == null || editing.previous() == null || world == null) {
      return;
    }
    if (!this.deleteArmed) {
      this.deleteArmed = true;
      rebuildWidgets();
      return;
    }
    this.map.deleteWaypoint(
        world,
        editing.previous(),
        editing.personal(),
        status -> {
          if (status == 204) {
            stopSelecting();
          } else {
            this.deleteArmed = false;
            showSelectionStatus(
                Component.translatable("taerra.waypoint.failed", editing.previous()), true);
            rebuildWidgets();
          }
        });
  }

  private @Nullable Double heightAt(final double x, final double z) {
    final @Nullable List<List<Double>> points = this.editing == null ? null : this.editing.points();
    if (points != null) {
      for (final @NotNull List<Double> point : points) {
        if (point.get(0) == x && point.get(2) == z) {
          return point.get(1);
        }
      }
    }
    return null;
  }

  private void stroke(final double x, final double y) {
    final @Nullable Place place = overMap(x, y) ? this.view.placeAt(this.map, x, y) : null;
    final @Nullable ChunkSelection selection = this.selection;
    if (place == null || selection == null) {
      return;
    }
    this.reach = place;
    if (this.tool == SelectionTool.BRUSH) {
      selection.brush(place.x(), place.z(), this.brushSize);
    } else if (this.tool == SelectionTool.LASSO) {
      final double @NotNull [] last = this.lasso.getLast();
      if (Math.abs(last[0] - place.x() - .5) + Math.abs(last[1] - place.z() - .5) >= 1) {
        this.lasso.add(new double[] {place.x() + .5, place.z() + .5});
      }
    }
  }

  private void showSelectionStatus(final @NotNull Component status, final boolean failed) {
    this.selectionStatus = status;
    this.selectionFailed = failed;
    this.selectionStatusUntil = System.currentTimeMillis() + STATUS_MILLIS;
  }

  private void selectionChanged() {
    this.selectionStatus = null;
    this.selectionFailed = false;
    updateDrawing();
  }

  private @NotNull List<double[]> copyPath() {
    return this.path.stream().map(double[]::clone).toList();
  }

  private static boolean samePath(
      final @NotNull List<double[]> a, final @NotNull List<double[]> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!Arrays.equals(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  private void remember(final @NotNull Edit edit) {
    this.history.push(edit);
    while (this.history.size() > HISTORY) {
      this.history.removeLast();
    }
    this.future.clear();
  }

  private void undo() {
    step(this.history, this.future);
  }

  private void redo() {
    step(this.future, this.history);
  }

  private void step(final @NotNull Deque<Edit> from, final @NotNull Deque<Edit> to) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null || this.stroking || from.isEmpty()) {
      return;
    }
    final @NotNull Edit edit = from.pop();
    final ChunkSelection.@Nullable Change change = edit.change();
    if (change != null) {
      if (from == this.history) {
        selection.revert(change);
      } else {
        selection.replay(change);
      }
      to.push(edit);
    } else if (edit.points() != null) {
      to.push(new Edit(copyPath(), null));
      this.path.clear();
      this.path.addAll(edit.points().stream().map(double[]::clone).toList());
    }
    selectionChanged();
  }

  private void setBrushSize(final int size) {
    if (this.selection != null && size != this.brushSize) {
      this.brushSize = size;
      rebuildWidgets();
    }
  }

  private void setEraseMode(final boolean erase) {
    if (this.selection != null && erase != this.eraseMode) {
      this.eraseMode = erase;
      rebuildWidgets();
    }
  }

  private void updateHover(final double x, final double y) {
    final @Nullable ChunkSelection selection = this.selection;
    @NotNull List<Long> hover = List.of();
    if (selection != null
        && !this.stroking
        && this.tool != SelectionTool.POINTS
        && this.menu == null
        && x >= 0) {
      final @Nullable Place place = this.view.placeAt(this.map, x, y);
      if (place != null) {
        hover =
            ChunkSelection.footprint(
                place.x(), place.z(), this.tool == SelectionTool.BRUSH ? this.brushSize : 1);
      }
    }
    final boolean erase = this.eraseMode != this.minecraft.hasShiftDown();
    if (!hover.equals(this.hover) || erase != this.hoverErase) {
      this.hover = hover;
      this.hoverErase = erase;
      updateDrawing();
    }
  }

  private @NotNull List<Drawing.Area> others() {
    final @Nullable String world = this.view.world();
    if (world == null) {
      return List.of();
    }
    final @NotNull Regions regions = this.map.regions(world);
    if (regions != this.othersOf || !Objects.equals(this.othersWithout, this.regionId)) {
      this.othersOf = regions;
      this.othersWithout = this.regionId;
      final @NotNull List<Drawing.Area> areas = new ArrayList<>();
      for (final Regions.@NotNull Region other :
          regions.regions() == null ? List.<Regions.Region>of() : regions.regions()) {
        if (other.chunks() == null || other.id().equals(this.regionId)) {
          continue;
        }
        final @NotNull Set<Long> chunks = new HashSet<>();
        for (final int @NotNull [] chunk : other.chunks()) {
          chunks.add(ChunkSelection.key(chunk[0], chunk[1]));
        }
        areas.add(
            new Drawing.Area(
                Set.copyOf(chunks),
                ChunkSelection.outline(chunks),
                other.color() == null ? 0xFFFFFF : Colors.argb(other.color()) & 0xFFFFFF));
      }
      this.others = List.copyOf(areas);
    }
    return this.others;
  }

  private void updateDrawing() {
    final @Nullable ChunkSelection selection = this.selection;
    this.view.select(
        selection == null
            ? null
            : new Drawing(
                selection,
                outline(),
                this.erasing,
                List.copyOf(this.path),
                this.kind == Kind.AREA,
                this.smooth,
                this.kind == Kind.CHUNKS ? null : Colors.argb(this.color),
                this.editingId,
                this.hover,
                this.hoverErase,
                this.kind == Kind.REGION ? others() : List.of()));
    if (this.saveButton != null && !this.stroking) {
      this.saveButton.active = this.kind == Kind.REGION ? regionReady() : shape() != null;
    }
    if (this.undoButton != null) {
      this.undoButton.active = !this.history.isEmpty();
    }
    if (this.redoButton != null) {
      this.redoButton.active = !this.future.isEmpty();
    }
  }

  private double @Nullable [][] outline() {
    final @Nullable Place anchor = this.anchor;
    final @Nullable Place reach = this.reach;
    if (!this.stroking || anchor == null || reach == null) {
      return null;
    }
    return switch (this.tool) {
      case BRUSH, POINTS -> null;
      case LASSO -> this.lasso.toArray(double[][]::new);
      case RECTANGLE -> {
        final int @NotNull [] box =
            ChunkSelection.boxChunks(anchor.x(), anchor.z(), reach.x(), reach.z());
        final int x0 = box[0] << 4;
        final int z0 = box[1] << 4;
        final int x1 = (box[2] + 1) << 4;
        final int z1 = (box[3] + 1) << 4;
        yield new double[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
      }
    };
  }

  private void addSelectionButtons(final @NotNull LinearLayout footer) {
    final @Nullable Account account = this.map.account();
    final @NotNull List<Button> tools = new ArrayList<>();
    for (final @NotNull SelectionTool tool : tools()) {
      final @NotNull Button button =
          Button.builder(Component.translatable(tool.key()), _ -> setTool(tool))
              .tooltip(
                  Tooltip.create(
                      Component.translatable(tool.key() + ".tooltip", tool.ordinal() + 1)))
              .build();
      button.active = tool != this.tool;
      tools.add(button);
    }
    final @NotNull List<AbstractWidget> controls = new ArrayList<>();
    if (this.kind != Kind.LINE) {
      if (this.tool != SelectionTool.POINTS) {
        for (final boolean erase : new boolean[] {false, true}) {
          final @NotNull String key = erase ? "taerra.selection.erase" : "taerra.selection.paint";
          final @NotNull Button mode =
              Button.builder(Component.translatable(key), _ -> setEraseMode(erase))
                  .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                  .build();
          mode.active = erase != this.eraseMode;
          controls.add(mode);
        }
      }
      if (this.tool == SelectionTool.BRUSH) {
        for (final int size : BRUSH_SIZES) {
          final @NotNull Button button =
              Button.builder(Component.literal(size + "×" + size), _ -> setBrushSize(size))
                  .tooltip(Tooltip.create(Component.translatable("taerra.selection.size")))
                  .build();
          button.active = size != this.brushSize;
          controls.add(button);
        }
      }
      this.undoButton =
          new IconButton(
              20, IconButton.UNDO, Component.translatable("taerra.selection.undo"), this::undo);
      this.undoButton.setTooltip(
          Tooltip.create(Component.translatable("taerra.selection.undo.tooltip")));
      this.undoButton.active = !this.history.isEmpty();
      this.redoButton =
          new IconButton(
              20, IconButton.REDO, Component.translatable("taerra.selection.redo"), this::redo);
      this.redoButton.setTooltip(
          Tooltip.create(Component.translatable("taerra.selection.redo.tooltip")));
      this.redoButton.active = !this.future.isEmpty();
      controls.add(this.undoButton);
      controls.add(this.redoButton);
    } else {
      this.undoButton = null;
      this.redoButton = null;
    }
    final @NotNull List<AbstractWidget> actions = new ArrayList<>();
    if (this.kind == Kind.CHUNKS) {
      if (account != null && account.renderArea()) {
        actions.add(actionButton("render"));
      }
      actions.add(
          Button.builder(Component.translatable("taerra.selection.done"), _ -> stopSelecting())
              .build());
    } else {
      if (this.kind == Kind.LINE) {
        actions.add(
            CycleButton.onOffBuilder(this.smooth)
                .create(
                    0,
                    0,
                    0,
                    20,
                    Component.translatable("taerra.selection.smooth"),
                    (_, value) -> {
                      this.smooth = value;
                      selectionChanged();
                    }));
      }
      if (this.editing != null || this.kind == Kind.REGION && this.regionId != null) {
        actions.add(
            Button.builder(
                    Component.translatable(
                            this.deleteArmed
                                ? "taerra.waypoint.confirm_delete"
                                : "taerra.waypoint.delete")
                        .withStyle(ChatFormatting.RED),
                    _ -> {
                      if (this.kind == Kind.REGION) {
                        deleteRegion();
                      } else {
                        delete();
                      }
                    })
                .build());
      }
      actions.add(
          Button.builder(Component.translatable("gui.cancel"), _ -> stopSelecting()).build());
      this.saveButton =
          Button.builder(Component.translatable("taerra.selection.save"), _ -> save()).build();
      this.saveButton.active =
          !this.sending && (this.kind == Kind.REGION ? regionReady() : shape() != null);
      actions.add(this.saveButton);
    }
    final @NotNull List<AbstractWidget> all = new ArrayList<>(tools);
    all.addAll(controls);
    all.addAll(actions);
    if (fit(all)) {
      all.forEach(footer::addChild);
      return;
    }
    final @NotNull List<AbstractWidget> first = new ArrayList<>(tools);
    first.addAll(controls);
    if (fit(first)) {
      fit(actions);
      addRows(footer, first, actions);
    } else {
      fit(tools);
      fit(controls);
      fit(actions);
      addRows(footer, tools, controls, actions);
    }
  }

  @SafeVarargs
  private void addRows(
      final @NotNull LinearLayout footer, final @NotNull List<? extends AbstractWidget>... rows) {
    final @NotNull LinearLayout column = footer.addChild(LinearLayout.vertical().spacing(4));
    int count = 0;
    for (final @NotNull List<? extends AbstractWidget> row : rows) {
      if (!row.isEmpty()) {
        row.forEach(column.addChild(LinearLayout.horizontal().spacing(6))::addChild);
        count++;
      }
    }
    this.layout.setFooterHeight(
        HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT + 24 * Math.max(0, count - 1));
  }

  private static int wanted(final @NotNull List<? extends AbstractWidget> row) {
    return row.stream().mapToInt(AbstractWidget::getWidth).sum() + 6 * Math.max(0, row.size() - 1);
  }

  private boolean fit(final @NotNull List<? extends AbstractWidget> row) {
    final int room = this.width - 16 - 6 * (row.size() - 1);
    int fixed = 0;
    int wanted = 0;
    for (final @NotNull AbstractWidget button : row) {
      if (button instanceof IconButton) {
        fixed += button.getWidth();
      } else {
        button.setWidth(Math.max(MIN_SELECTION_BUTTON, this.font.width(button.getMessage()) + 12));
        wanted += button.getWidth();
      }
    }
    if (wanted + fixed > room && wanted > 0) {
      for (final @NotNull AbstractWidget button : row) {
        if (!(button instanceof IconButton)) {
          button.setWidth(button.getWidth() * Math.max(0, room - fixed) / wanted);
        }
      }
    }
    return wanted + fixed <= room;
  }

  private @NotNull Button actionButton(final @NotNull String action) {
    final @NotNull Button button =
        Button.builder(Component.translatable("taerra.selection." + action), _ -> area(action))
            .build();
    button.active = !this.sending;
    return button;
  }

  private void area(final @NotNull String action) {
    final @Nullable ChunkSelection selection = this.selection;
    final @Nullable String world = this.view.world();
    if (selection == null || world == null || this.sending) {
      return;
    }
    if (selection.isEmpty()) {
      showSelectionStatus(Component.translatable("taerra.selection.empty"), true);
      return;
    }
    this.sending = true;
    rebuildWidgets();
    this.map.area(
        action,
        world,
        Set.copyOf(selection.chunks()),
        status -> {
          this.sending = false;
          if (selection == this.selection) {
            if (status == 202) {
              selection.clear();
              selectionChanged();
            }
            showSelectionStatus(
                Component.translatable(
                    status == 202
                        ? "taerra.selection.started." + action
                        : status == 409 ? "taerra.selection.busy" : "taerra.selection.failed"),
                status != 202);
          }
          rebuildWidgets();
        });
  }

  private record Edit(@Nullable List<double[]> points, ChunkSelection.@Nullable Change change) {}

  private enum Kind {
    CHUNKS,
    REGION,
    AREA,
    LINE
  }

  private enum SelectionTool {
    BRUSH,
    RECTANGLE,
    LASSO,
    POINTS;

    private @NotNull String key() {
      return "taerra.selection.tool." + name().toLowerCase(Locale.ROOT);
    }
  }

  private void copy(
      final @NotNull PlaceMenu menu,
      final PlaceMenu.@NotNull Item item,
      final @NotNull String text) {
    this.minecraft.keyboardHandler.setClipboard(text);
    item.show(Component.translatable("taerra.menu.copied"), true);
    menu.closeIn(700);
  }

  private void moveWaypoint(final double mouseX, final double mouseY) {
    final @Nullable Waypoint waypoint = this.moving;
    final @Nullable Place place = this.view.placeAt(this.map, mouseX, mouseY);
    if (waypoint == null || place == null || this.view.world() == null) {
      return;
    }
    this.moving = null;
    this.map.saveWaypoint(
        waypoint.at(place.x(), place.standing(), place.z()),
        false,
        status -> {
          if (status != 204) {
            notice(Component.translatable("taerra.waypoint.failed", waypoint.name()));
          }
        });
  }

  private void renderWorld(
      final @NotNull PlaceMenu menu,
      final PlaceMenu.@NotNull Item item,
      final @NotNull String world,
      final boolean cancel) {
    item.show(item.getMessage(), true);
    this.map.render(
        cancel ? "cancel" : "full",
        world,
        status -> {
          final boolean done = status == 202 || status == 204;
          item.show(
              Component.translatable(
                  done
                      ? cancel ? "taerra.menu.render_canceled" : "taerra.menu.render_started"
                      : status == 409
                          ? cancel ? "taerra.menu.render_idle" : "taerra.menu.render_busy"
                          : "taerra.selection.failed"),
              false);
          menu.closeIn(done ? 1200 : 2500);
        });
  }

  private void teleport(
      final @NotNull PlaceMenu menu,
      final PlaceMenu.@NotNull Item item,
      final String world,
      final int x,
      final @Nullable Integer y,
      final int z) {
    item.show(Component.translatable("taerra.menu.teleport"), true);
    this.map.teleport(
        world,
        x,
        y,
        z,
        status -> {
          if (status == 204) {
            onClose();
            Toasts.show(
                Component.translatable("taerra.teleported"),
                Component.translatable("taerra.teleported.detail", x, z));
            return;
          }
          item.show(
              Component.translatable(status == 409 ? "taerra.menu.offline" : "taerra.menu.failed"),
              false);
          menu.closeIn(2000);
        });
  }
}
