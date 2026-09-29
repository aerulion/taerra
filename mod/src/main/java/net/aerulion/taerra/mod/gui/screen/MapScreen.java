package net.aerulion.taerra.mod.gui.screen;

import java.util.List;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.gui.widget.CompassButton;
import net.aerulion.taerra.mod.gui.widget.LayerList;
import net.aerulion.taerra.mod.gui.widget.PanelList;
import net.aerulion.taerra.mod.gui.widget.PlaceMenu;
import net.aerulion.taerra.mod.gui.widget.PlayerList;
import net.aerulion.taerra.mod.gui.widget.RenderProgressOverlay;
import net.aerulion.taerra.mod.gui.widget.WorldPicker;
import net.aerulion.taerra.mod.input.TaerraKeys;
import net.aerulion.taerra.mod.map.MapView;
import net.aerulion.taerra.mod.map.MarkerHover;
import net.aerulion.taerra.mod.map.Place;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.Toasts;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapListener;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.Marker;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.Waypoint;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class MapScreen extends Screen {

  private static final Identifier LIST_BACKGROUND =
      Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
  private static final int BUTTON_WIDTH = 74;
  private static final int PANEL_BUTTON_WIDTH = 74;
  private static final int SPAWN_BUTTON_WIDTH = 50;
  private static final int SCOPE_HEIGHT = 24;
  private static final double CLICK_SLOP = 3;
  private static final int COMPASS_MARGIN = 6;
  private static final int NEAR_ZOOM = 2;
  private static final int TOP_DOWN = -1;
  private static final List<Integer> VIEWS = List.of(TOP_DOWN, 0, 1, 2, 3);

  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final @NotNull WebMap map;
  private final @NotNull MapView view;
  private final Settings settings;
  private final @NotNull PlayerList players;
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
  private @Nullable Component notice;
  private long noticeUntil;

  public MapScreen(
      final @NotNull WebMap map, final @NotNull MapView view, final Settings settings) {
    super(Component.translatable("taerra.map"));
    this.map = map;
    this.view = view;
    this.settings = settings;
    this.players = new PlayerList(map, settings, view::world, this::worldName, this::follow);
    this.layers = new LayerList(map);
    this.worldPicker = new WorldPicker(map::worlds, view::world, this::pickWorld);
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
    if (this.menu != null) {
      this.menu.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    if (this.worldPicker.isOpen()) {
      layoutWorlds();
      this.worldPicker.render(graphics, mouseX, mouseY, partialTick);
    }
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
  }

  @Override
  public boolean keyPressed(final KeyEvent event) {
    if (this.worldPicker.isOpen()) {
      if (event.isEscape()) {
        openWorlds(false);
        return true;
      }
      if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
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
    if (TaerraKeys.OPEN_MAP.matches(event)) {
      onClose();
      return true;
    }
    if (shortcut(event)) {
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  protected void init() {
    this.layout.removeChildren();
    final List<World> worlds = this.map.worlds();
    if (this.map.world(this.view.world()) == null && !worlds.isEmpty()) {
      selectWorld(worlds.getFirst());
    }
    this.layout.addTitleHeader(this.title, this.font);
    this.playersButton =
        this.layout.addToHeader(
            Button.builder(
                    Component.translatable("taerra.map.players"),
                    _ -> setPanels(!this.playersOpen, this.layersOpen))
                .width(PANEL_BUTTON_WIDTH)
                .build(),
            settings -> settings.align(0, .5f).paddingLeft(8));
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
    if (world == null) {
      footer.addChild(
                  Button.builder(Component.translatable("taerra.map.loading"), _ -> {})
                      .width(2 * BUTTON_WIDTH + 6)
                      .build())
              .active =
          false;
    } else {
      this.worldButton =
          footer.addChild(
              Button.builder(world.label(), _ -> openWorlds(!this.worldPicker.isOpen()))
                  .tooltip(Tooltip.create(Component.translatable("taerra.map.world.tooltip")))
                  .width(BUTTON_WIDTH)
                  .build());
      footer.addChild(
          CycleButton.builder(
                  MapScreen::viewName, this.view.isometric() ? this.view.rotation() : TOP_DOWN)
              .withValues(VIEWS)
              .withTooltip(_ -> Tooltip.create(viewTooltip()))
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  BUTTON_WIDTH,
                  20,
                  Component.translatable("taerra.map.view"),
                  (_, selected) -> setView(selected)));
    }
    footer.addChild(
        Button.builder(
                Component.translatable("menu.options"),
                _ -> this.minecraft.setScreenAndShow(new SettingsScreen(this, this.settings)))
            .width(BUTTON_WIDTH)
            .build());
    footer.addChild(
        Button.builder(Component.translatable("gui.done"), _ -> onClose())
            .width(BUTTON_WIDTH)
            .build());

    if (this.worldButton == null && this.worldPicker.isOpen()) {
      this.worldPicker.setOpen(false);
    }
    final @Nullable EditBox search = this.worldPicker.createSearch(this.font, worlds.size());
    if (search != null) {
      addRenderableWidget(search);
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
    this.layout.arrangeElements();
    final int height = mapBottom() - mapTop();
    this.players.updateSizeAndPosition(PanelList.WIDTH, height - SCOPE_HEIGHT, 0, mapTop());
    this.layers.updateSizeAndPosition(
        PanelList.WIDTH, height, this.width - PanelList.WIDTH, mapTop());
    if (this.scope != null) {
      this.scope.setPosition(4, mapBottom() - SCOPE_HEIGHT + 2);
    }
    if (this.compass != null) {
      this.compass.setPosition(
          mapRight() - COMPASS_MARGIN - CompassButton.SIZE, mapTop() + COMPASS_MARGIN);
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
      if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !overMap(event.x(), event.y())) {
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
    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
      this.moving = null;
      openPlaceMenu(event.x(), event.y());
      return true;
    }
    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      this.dragging = true;
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
    if (this.dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      this.dragging = false;
      if (this.dragDistance <= CLICK_SLOP) {
        final @Nullable MarkerHover hover = this.view.hover();
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
    this.view.setWorld(world.name());
    this.moving = null;
    if (config != null) {
      this.view.centerOn(config, world.spawn().x(), world.spawn().y(), world.spawn().z());
      zoomNear();
    }
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
    final @Nullable World world = player == null ? null : this.map.world(player.world());
    if (world == null) {
      setFollowed(null);
      return;
    }
    if (!world.name().equals(this.view.world())) {
      this.view.setWorld(world.name());
      this.moving = null;
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
    final @Nullable CompassButton compass = this.compass;
    return compass != null
        && compass.visible
        && x >= compass.getX()
        && x < compass.getRight()
        && y >= compass.getY()
        && y < compass.getBottom();
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
        final @Nullable Place place = this.view.placeAt(this.map, mouseX, mouseY);
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
    final @Nullable CompassButton compass = this.compass;
    if (compass != null && compass.visible) {
      final boolean covered = this.menu != null && this.menu.isMouseOver(mouseX, mouseY);
      compass.extractRenderState(
          graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, partialTick);
    }
    if (this.moving != null) {
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.waypoint.move_hint", this.moving.name()),
          middle,
          bottom - 24,
          0xFFFFFF55);
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
    if (account != null && account.waypoints()) {
      menu.add(
          Component.translatable("taerra.menu.waypoint"),
          _ ->
              this.minecraft.setScreenAndShow(
                  new WaypointScreen(
                      this, this.map, Waypoint.create(world, place.x(), standing, place.z()))));
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
    showMenu(menu, mouseX, mouseY);
  }

  private void openMarkerMenu(
      final @NotNull MarkerHover hover, final double mouseX, final double mouseY) {
    final Marker marker = hover.marker();
    final @Nullable Account account = this.map.account();
    final @Nullable String world = this.view.world();
    if (account == null
        || world == null
        || !hover.layer().waypoints()
        || !"point".equals(marker.type())
        || marker.position() == null
        || marker.label() == null) {
      return;
    }
    final @NotNull Waypoint waypoint = Waypoint.of(world, hover.layer(), marker);
    final @NotNull PlaceMenu menu = new PlaceMenu(Component.literal(marker.label()));
    if (account.teleport()) {
      menu.add(
          Component.translatable("taerra.waypoint.teleport"),
          item -> teleport(menu, item, world, waypoint.x(), waypoint.y(), waypoint.z()));
    }
    if (marker.id().startsWith("waypoint:") && account.waypoints()) {
      menu.add(
          Component.translatable("taerra.waypoint.move"),
          _ -> {
            closeMenu();
            this.moving = waypoint;
          });
      menu.add(
          Component.translatable("taerra.waypoint.edit_short"),
          _ -> this.minecraft.setScreenAndShow(new WaypointScreen(this, this.map, waypoint)));
    }
    if (!menu.isEmpty()) {
      showMenu(menu, mouseX, mouseY);
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
