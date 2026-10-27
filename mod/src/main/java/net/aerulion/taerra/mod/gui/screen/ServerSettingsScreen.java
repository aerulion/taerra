package net.aerulion.taerra.mod.gui.screen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.gui.widget.IconChoice;
import net.aerulion.taerra.mod.map.MapIcons;
import net.aerulion.taerra.mod.map.Projection;
import net.aerulion.taerra.mod.util.PlayerInfos;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapListener;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.MapIcon;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ServerSettingsScreen extends Screen implements WebMapListener {

  private static final Gson GSON = new Gson();
  private static final Pattern NOT_NAME = Pattern.compile("[^\\p{L}\\p{N} _.,:'&()#!?+-]");
  private static final Pattern NOT_DIGIT = Pattern.compile("\\D");
  private static final Pattern NOT_COORDINATE = Pattern.compile("[^0-9-]");
  private static final Pattern SCALE = Pattern.compile("(\\d{1,5})(px|blocks?)");
  private static final List<String> SHAPES = List.of("none", "circle", "square", "rectangle");
  private static final List<String> VIEWS = List.of("both", "top_down", "isometric");
  private static final int MAX_COORDINATE = 30_000_000;
  private static final String ORDER = "order";
  private static final int ROW_WIDTH = 380;
  private static final int TAB_PADDING = 20;
  private static final int CONTROL_WIDTH = 120;
  private static final int UNIT_WIDTH = 44;
  private static final int INDENT = 14;
  private static final int SLAB = 0x66000000;
  private static final int SLAB_HOVER = 0x80000000;
  private static final int MUTED = 0xFFA0A0A0;
  private static final int ACCENT = 0xFFD4732F;
  private static final int GOLD = 0xFFFFCF3F;
  private static final int GREEN = 0xFF52A535;
  private static final int RED = 0xFFE34A4A;
  private static final int TEXT = 0xFFE0E0E0;
  private static final long STATUS_MILLIS = 6000;
  private static final long FLASH_MILLIS = 1800;
  private static final List<Section> SECTIONS =
      List.of(
          new Section("worlds", List.of()),
          new Section("limits", List.of()),
          new Section("views", List.of()),
          new Section("map", List.of("web.name", "web.public-url", "join-hint.interval-days")),
          new Section(
              "layers",
              List.of(
                  "markers.spawn.enabled",
                  "markers.spawn.shown-by-default",
                  "markers.worldborder.enabled",
                  "markers.worldborder.shown-by-default",
                  "markers.worldguard.enabled",
                  "markers.worldguard.shown-by-default",
                  "markers.towny.enabled",
                  "markers.towny.shown-by-default",
                  "markers.griefprevention.enabled",
                  "markers.griefprevention.shown-by-default",
                  "markers.plotsquared.enabled",
                  "markers.plotsquared.shown-by-default",
                  "markers.loaded-chunks.enabled",
                  "markers.loaded-chunks.shown-by-default")),
          new Section(
              "rendering",
              List.of(
                  "rendering.update-interval",
                  "rendering.render-threads",
                  "cache.max-tiles",
                  "cache.max-chunks")),
          new Section(
              "storage",
              List.of(
                  "storage.type",
                  "storage.database.host",
                  "storage.database.port",
                  "storage.database.name",
                  "storage.database.user",
                  "storage.database.password")),
          new Section("web", List.of("web.port", "web.host")));
  private static final String STORAGE_TYPE = "setting:storage.type";

  private final @NotNull Screen parent;
  private final @NotNull WebMap map;
  private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final TabManager tabManager =
      new TabManager(this::addRenderableWidget, this::removeWidget);
  private final List<SectionTab> tabs = new ArrayList<>();
  private final Map<String, JsonElement> base = new LinkedHashMap<>();
  private final Map<String, JsonElement> draft = new LinkedHashMap<>();
  private final Map<String, Conflict> conflicts = new LinkedHashMap<>();
  private final Map<String, Long> flashing = new HashMap<>();
  private final Map<String, EditBox> boxes = new HashMap<>();
  private final List<String> display = new ArrayList<>();
  private List<ServerSettings.Editor> editors = List.of();
  private @Nullable MenuTabBar bar;
  private @Nullable Button save;
  private @Nullable Button cancel;
  private boolean discardArmed;
  private @Nullable ServerSettings view;
  private boolean loaded;
  private boolean saving;
  private boolean refreshing;
  private boolean refreshAgain;
  private @NotNull Component status = Component.empty();
  private int statusColor = MUTED;
  private long statusUntil;
  private int selected;
  private int rowsWidth;

  public ServerSettingsScreen(final @NotNull Screen parent, final @NotNull WebMap map) {
    super(Component.translatable("taerra.settings"));
    this.parent = parent;
    this.map = map;
  }

  private static @NotNull Component text(
      final @NotNull String key, final @NotNull String fallback) {
    return Language.getInstance().has(key)
        ? Component.translatable(key)
        : Component.literal(fallback);
  }

  private static @Nullable Component optional(final @NotNull String key) {
    return Language.getInstance().has(key) ? Component.translatable(key) : null;
  }

  private static int environmentColor(final @Nullable String environment) {
    return switch (environment == null ? "" : environment) {
      case "NORMAL" -> GREEN;
      case "NETHER" -> RED;
      case "THE_END" -> 0xFFA77BFF;
      default -> 0xFF58585A;
    };
  }

  private static @NotNull String settingField(final @NotNull String key) {
    return "setting:" + key;
  }

  private static @NotNull String worldField(final @NotNull String id, final @NotNull String part) {
    return "world:" + id + ":" + part;
  }

  private static @NotNull String layerField(final @NotNull String id) {
    return "layer:" + id;
  }

  private static @NotNull String layerIconField(final @NotNull String id) {
    return "layer-icon:" + id;
  }

  private static @NotNull String layerScaleField(final @NotNull String id) {
    return "layer-scale:" + id;
  }

  private static @Nullable Integer parseScale(final @NotNull String text) {
    final @NotNull Matcher matcher = SCALE.matcher(text);
    if (!matcher.matches() || Integer.bitCount(Integer.parseInt(matcher.group(1))) != 1) {
      return null;
    }
    final int exponent = Integer.numberOfTrailingZeros(Integer.parseInt(matcher.group(1)));
    return matcher.group(2).equals("px") ? exponent : -exponent;
  }

  private static @NotNull String scaleText(final @Nullable Integer scale) {
    if (scale == null) {
      return "";
    }
    return scale >= 0 ? (1 << scale) + "px" : (1 << -scale) + "blocks";
  }

  private static @NotNull Component describeScale(final @Nullable Integer scale) {
    if (scale == null) {
      return Component.translatable("taerra.settings.layer_scale.always");
    }
    return scale >= 0
        ? Component.translatable("taerra.settings.layer_scale.pixels", 1 << scale)
        : Component.translatable("taerra.settings.layer_scale.blocks", 1 << -scale);
  }

  private static boolean same(final @Nullable JsonElement a, final @Nullable JsonElement b) {
    return Objects.equals(a == null ? JsonNull.INSTANCE : a, b == null ? JsonNull.INSTANCE : b);
  }

  private static @NotNull List<String> ids(final @Nullable JsonElement order) {
    final @NotNull List<String> ids = new ArrayList<>();
    if (order != null && order.isJsonArray()) {
      order.getAsJsonArray().forEach(id -> ids.add(id.getAsString()));
    }
    return ids;
  }

  private static @NotNull JsonArray array(final @NotNull List<String> ids) {
    final @NotNull JsonArray array = new JsonArray();
    ids.forEach(array::add);
    return array;
  }

  private static boolean sameOrder(final @Nullable JsonElement a, final @Nullable JsonElement b) {
    final @NotNull List<String> first = ids(a);
    final @NotNull List<String> second = ids(b);
    return first.stream()
        .filter(second::contains)
        .toList()
        .equals(second.stream().filter(first::contains).toList());
  }

  private static boolean on(final @Nullable JsonElement value) {
    return value != null
        && value.isJsonPrimitive()
        && value.getAsJsonPrimitive().isBoolean()
        && value.getAsBoolean();
  }

  private static @NotNull String string(final @Nullable JsonElement value) {
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  private static @Nullable String author(
      final @NotNull ServerSettings next, final @NotNull String field) {
    final ServerSettings.@Nullable Author author =
        next.authors() == null ? null : next.authors().get(field);
    return author == null ? null : author.name();
  }

  private static void clean(
      final @NotNull EditBox box,
      final @NotNull Pattern drop,
      final @NotNull Consumer<String> then) {
    box.setResponder(
        value -> {
          final @NotNull String clean = drop.matcher(value).replaceAll("");
          if (clean.equals(value)) {
            then.accept(value);
          } else {
            box.setValue(clean);
          }
        });
  }

  private static boolean outOfRange(
      final ServerSettings.@Nullable SettingInfo setting, final @Nullable JsonElement value) {
    if (setting == null || !setting.kind().equals("number")) {
      return false;
    }
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      return true;
    }
    final double number = value.getAsDouble();
    return number != Math.rint(number)
        || setting.min() != null && number < setting.min()
        || setting.max() != null && number > setting.max();
  }

  private static boolean isLimit(final @NotNull String field) {
    return field.startsWith("world:") && field.endsWith(":limit");
  }

  private static boolean isStorage(final @NotNull String field) {
    return field.startsWith("setting:storage.");
  }

  private static boolean isViews(final @NotNull String field) {
    return field.startsWith("world:") && field.endsWith(":views");
  }

  private static @NotNull String viewsOf(final @Nullable JsonElement value) {
    final @NotNull String views = string(value);
    return VIEWS.contains(views) ? views : "both";
  }

  private static @NotNull List<String> partsOf(final @NotNull String views) {
    return views.equals("both") ? List.of("top_down", "isometric") : List.of(views);
  }

  private static boolean addsView(
      final @Nullable JsonElement before, final @Nullable JsonElement after) {
    return !partsOf(viewsOf(before)).containsAll(partsOf(viewsOf(after)));
  }

  private static @NotNull String shapeOf(final @Nullable JsonElement value) {
    return value != null
            && value.isJsonObject()
            && value.getAsJsonObject().get("shape") instanceof final JsonPrimitive shape
            && SHAPES.contains(shape.getAsString())
        ? shape.getAsString()
        : "none";
  }

  private static @Nullable Integer bound(
      final @Nullable JsonElement value, final @NotNull String key) {
    if (value == null || !value.isJsonObject()) {
      return null;
    }
    final @Nullable JsonElement bound = value.getAsJsonObject().get(key);
    return bound != null
            && bound.isJsonPrimitive()
            && bound.getAsJsonPrimitive().isNumber()
            && bound.getAsDouble() == Math.rint(bound.getAsDouble())
        ? bound.getAsInt()
        : null;
  }

  private static @NotNull JsonObject limit(
      final @NotNull String shape,
      final @Nullable Integer minX,
      final @Nullable Integer minZ,
      final @Nullable Integer maxX,
      final @Nullable Integer maxZ) {
    final @NotNull JsonObject value = new JsonObject();
    value.addProperty("shape", shape);
    if (!shape.equals("none")) {
      final @NotNull String[] keys = {"minX", "minZ", "maxX", "maxZ"};
      final @Nullable Integer[] bounds = {minX, minZ, maxX, maxZ};
      for (int i = 0; i < keys.length; i++) {
        value.add(keys[i], bounds[i] == null ? JsonNull.INSTANCE : new JsonPrimitive(bounds[i]));
      }
    }
    return value;
  }

  private static boolean badLimit(final @Nullable JsonElement value) {
    final @NotNull String shape = shapeOf(value);
    if (shape.equals("none")) {
      return false;
    }
    final @Nullable Integer minX = bound(value, "minX");
    final @Nullable Integer minZ = bound(value, "minZ");
    final @Nullable Integer maxX = bound(value, "maxX");
    final @Nullable Integer maxZ = bound(value, "maxZ");
    if (minX == null || minZ == null || maxX == null || maxZ == null) {
      return true;
    }
    if (Math.max(Math.max(Math.abs(minX), Math.abs(minZ)), Math.max(Math.abs(maxX), Math.abs(maxZ)))
        > MAX_COORDINATE) {
      return true;
    }
    final int width = maxX - minX;
    return width < 0 || maxZ - minZ < 0 || (!shape.equals("rectangle") && width < 2);
  }

  private static @NotNull JsonObject reshape(
      final @Nullable JsonElement value, final @NotNull String shape) {
    final boolean known = !shapeOf(value).equals("none") && !badLimit(value);
    final int minX = known ? Objects.requireNonNull(bound(value, "minX")) : -1000;
    final int minZ = known ? Objects.requireNonNull(bound(value, "minZ")) : -1000;
    final int maxX = known ? Objects.requireNonNull(bound(value, "maxX")) : 1000;
    final int maxZ = known ? Objects.requireNonNull(bound(value, "maxZ")) : 1000;
    if (!shape.equals("circle") && !shape.equals("square")) {
      return limit(shape, minX, minZ, maxX, maxZ);
    }
    final int radius = Math.max(1, Math.round(Math.max(maxX - minX, maxZ - minZ) / 2f));
    final int x = Math.floorDiv(minX + maxX, 2);
    final int z = Math.floorDiv(minZ + maxZ, 2);
    return limit(shape, x - radius, z - radius, x + radius, z + radius);
  }

  private static @Nullable Integer coordinate(final @NotNull String text) {
    try {
      return Integer.parseInt(text);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static @NotNull Component describeLimit(final @Nullable JsonElement value) {
    final @NotNull String shape = shapeOf(value);
    if (shape.equals("none") || badLimit(value)) {
      return Component.translatable(
          shape.equals("none") ? "taerra.settings.limit.unset" : "taerra.settings.limit." + shape);
    }
    final int minX = Objects.requireNonNull(bound(value, "minX"));
    final int minZ = Objects.requireNonNull(bound(value, "minZ"));
    final int maxX = Objects.requireNonNull(bound(value, "maxX"));
    final int maxZ = Objects.requireNonNull(bound(value, "maxZ"));
    return shape.equals("rectangle")
        ? Component.translatable("taerra.settings.limit.box", minX, minZ, maxX, maxZ)
        : Component.translatable(
            "taerra.settings.limit.round",
            Component.translatable("taerra.settings.limit." + shape),
            (maxX - minX) / 2,
            Math.floorDiv(minX + maxX, 2),
            Math.floorDiv(minZ + maxZ, 2));
  }

  private static @NotNull Component since(final long at) {
    final long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60000);
    if (minutes < 1) {
      return Component.translatable("taerra.devices.now");
    }
    if (minutes < 60) {
      return Component.translatable("taerra.devices.minutes", minutes);
    }
    return minutes < 1440
        ? Component.translatable("taerra.devices.hours", minutes / 60)
        : Component.translatable("taerra.devices.days", minutes / 1440);
  }

  @Override
  protected void init() {
    this.map.removeListener(this);
    this.map.addListener(this);
    this.map.setSettingsOpen(true);
    this.tabs.clear();
    for (final @NotNull Section section : SECTIONS) {
      this.tabs.add(new SectionTab(section));
    }
    this.bar =
        MenuTabBar.builder(this.tabManager, this.width)
            .addTabs(this.tabs.toArray(Tab[]::new))
            .build();
    addRenderableWidget(this.bar);
    final @NotNull LinearLayout footer =
        this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
    this.cancel =
        footer.addChild(
            Button.builder(CommonComponents.GUI_CANCEL, _ -> onClose()).width(120).build());
    updateCancel();
    this.save =
        footer.addChild(
            Button.builder(Component.translatable("taerra.settings.save"), _ -> save())
                .width(120)
                .build());
    this.layout.visitWidgets(this::addRenderableWidget);
    this.bar.selectTab(this.selected, false);
    repositionElements();
    if (this.loaded) {
      refill();
    } else {
      this.map.settings(this::opened);
    }
    updateStatus();
  }

  @Override
  protected void repositionElements() {
    if (this.bar == null) {
      return;
    }
    this.bar.arrangeElements(this.width);
    fitTabs();
    final int top = this.bar.getRectangle().bottom();
    this.layout.setHeaderHeight(top);
    this.layout.setFooterHeight(52);
    this.layout.arrangeElements();
    this.tabManager.setTabArea(
        new ScreenRectangle(0, top, this.width, this.height - this.layout.getFooterHeight() - top));
    if (this.loaded && this.rowsWidth != this.width) {
      refill();
    }
  }

  private void fitTabs() {
    final @NotNull List<TabButton> buttons =
        this.bar.children().stream().map(TabButton.class::cast).toList();
    final int[] widths =
        buttons.stream()
            .mapToInt(button -> this.font.width(button.getMessage()) + TAB_PADDING)
            .toArray();
    final int natural = Arrays.stream(widths).sum();
    final int total = Math.min(this.width - 28, Math.max(Math.min(400, this.width) - 28, natural));
    if (natural > total) {
      return;
    }
    final int extra = (total - natural) / buttons.size();
    int x = (this.width - total) / 2;
    for (int i = 0; i < buttons.size(); i++) {
      final @NotNull TabButton button = buttons.get(i);
      button.setWidth(widths[i] + extra);
      button.setX(x);
      x += button.getWidth();
    }
  }

  private int rowWidth() {
    return Math.min(ROW_WIDTH, this.width - 24);
  }

  @Override
  public void onClose() {
    if (!this.discardArmed && !changes().isEmpty()) {
      this.discardArmed = true;
      updateCancel();
      return;
    }
    this.minecraft.setScreenAndShow(this.parent);
  }

  private void disarm() {
    this.discardArmed = false;
    updateCancel();
  }

  private void updateCancel() {
    if (this.cancel != null) {
      this.cancel.setMessage(
          this.discardArmed
              ? Component.translatable("taerra.settings.discard").withStyle(ChatFormatting.RED)
              : CommonComponents.GUI_CANCEL);
    }
  }

  @Override
  public void removed() {
    this.map.setSettingsOpen(false);
    this.map.removeListener(this);
    super.removed();
  }

  @Override
  public void settingsChanged(final long revision) {
    if (this.view != null && revision > this.view.revision()) {
      refresh();
    }
  }

  @Override
  public void settingsEditors(final @NotNull List<ServerSettings.Editor> editors) {
    this.editors = List.copyOf(editors);
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (this.statusUntil > 0 && System.currentTimeMillis() > this.statusUntil) {
      this.statusUntil = 0;
      this.status = Component.empty();
      updateStatus();
    }
    if (!this.loaded || this.view == null) {
      graphics.centeredText(
          this.font,
          Component.translatable(
              this.loaded ? "taerra.settings.unavailable" : "taerra.settings.loading"),
          this.width / 2,
          this.height / 2 - 4,
          MUTED);
    }
    final int footerTop = this.height - this.layout.getFooterHeight();
    final int room = this.width - 16;
    final boolean cut = this.font.width(this.status) > room;
    graphics.centeredText(
        this.font,
        cut
            ? Component.literal(
                this.font.plainSubstrByWidth(this.status.getString(), room - this.font.width("…"))
                    + "…")
            : this.status,
        this.width / 2,
        footerTop + 4,
        this.statusColor);
    if (cut && mouseY >= footerTop + 2 && mouseY < footerTop + 14) {
      graphics.setTooltipForNextFrame(this.font, this.font.split(this.status, 320), mouseX, mouseY);
    }
    extractEditors(graphics, mouseX, mouseY, footerTop);
  }

  private void extractEditors(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final int footerTop) {
    final @Nullable Account account = this.map.account();
    final @Nullable String me = account == null ? null : account.uuid();
    int x = 8;
    final int y = footerTop + 25;
    for (final ServerSettings.@NotNull Editor editor : this.editors) {
      if (editor.uuid().equals(me) && editor.client().equals("mod")) {
        continue;
      }
      final @Nullable PlayerInfo info = PlayerInfos.find(editor.uuid());
      final @NotNull Component name =
          editor.uuid().equals(me)
              ? Component.translatable("taerra.settings.you")
              : Component.literal(editor.name());
      final int width = 16 + this.font.width(name) + 6;
      if (x + width > this.width / 2 - 130) {
        break;
      }
      graphics.fill(x - 1, y - 1, x + 13, y + 13, editor.client().equals("mod") ? ACCENT : GREEN);
      PlayerFaceExtractor.extractRenderState(
          graphics, info != null ? info.getSkin() : DefaultPlayerSkin.getDefaultSkin(), x, y, 12);
      graphics.text(this.font, name, x + 16, y + 2, 0xFFFFFFFF, true);
      if (mouseX >= x && mouseX < x + width && mouseY >= y - 1 && mouseY < y + 13) {
        graphics.setTooltipForNextFrame(
            this.font,
            Component.translatable(
                "taerra.settings.editing",
                name,
                Component.translatable(
                    editor.client().equals("mod")
                        ? "taerra.settings.in_game"
                        : "taerra.settings.in_browser")),
            mouseX,
            mouseY);
      }
      x += width + 6;
    }
  }

  private void opened(final @Nullable ServerSettings next) {
    this.loaded = true;
    if (next == null) {
      return;
    }
    accept(next);
    this.base.clear();
    this.draft.clear();
    this.conflicts.clear();
    next.values().entrySet().forEach(entry -> this.base.put(entry.getKey(), entry.getValue()));
    this.base.forEach((field, value) -> this.draft.put(field, value.deepCopy()));
    this.display.clear();
    arrange(ids(this.draft.get(ORDER)));
    refill();
    updateStatus();
  }

  private void accept(final @NotNull ServerSettings next) {
    this.view = next;
    this.editors = next.editors() == null ? List.of() : List.copyOf(next.editors());
  }

  private void refresh() {
    if (this.refreshing) {
      this.refreshAgain = true;
      return;
    }
    this.refreshing = true;
    this.map.settings(
        next -> {
          this.refreshing = false;
          if (next != null && this.view != null) {
            rebase(next);
          }
          if (this.refreshAgain) {
            this.refreshAgain = false;
            refresh();
          }
        });
  }

  private void rebase(final @NotNull ServerSettings next) {
    final @NotNull Map<String, JsonElement> now = new LinkedHashMap<>();
    next.values().entrySet().forEach(entry -> now.put(entry.getKey(), entry.getValue()));
    final @NotNull Set<String> fields = new LinkedHashSet<>(this.base.keySet());
    fields.addAll(now.keySet());
    fields.addAll(this.draft.keySet());
    final @NotNull Set<String> changed = new LinkedHashSet<>();
    for (final @NotNull String field : fields) {
      if (!now.containsKey(field)) {
        this.draft.remove(field);
        this.conflicts.remove(field);
        continue;
      }
      if (field.equals(ORDER)) {
        continue;
      }
      final @Nullable JsonElement mine = this.draft.get(field);
      final boolean touched = this.draft.containsKey(field) && !same(mine, this.base.get(field));
      if (!touched) {
        if (this.base.containsKey(field) && !same(mine, now.get(field))) {
          changed.add(field);
        }
        this.draft.put(field, now.get(field).deepCopy());
        this.conflicts.remove(field);
      } else if (same(mine, now.get(field))) {
        this.conflicts.remove(field);
      } else if (!same(this.base.get(field), now.get(field))) {
        this.conflicts.put(field, new Conflict(now.get(field).deepCopy(), author(next, field)));
      }
    }
    accept(next);
    if (same(this.draft.get(ORDER), this.base.get(ORDER))) {
      if (!sameOrder(this.draft.get(ORDER), now.get(ORDER))) {
        changed.add(ORDER);
      }
      this.draft.put(ORDER, now.get(ORDER).deepCopy());
      this.conflicts.remove(ORDER);
    } else if (same(this.draft.get(ORDER), now.get(ORDER))) {
      this.conflicts.remove(ORDER);
    } else if (!sameOrder(this.base.get(ORDER), now.get(ORDER))) {
      this.conflicts.put(ORDER, new Conflict(now.get(ORDER).deepCopy(), author(next, ORDER)));
    }
    arrange(ids(this.draft.get(ORDER)));
    fixOrder();
    this.base.clear();
    this.base.putAll(now);
    final long until = System.currentTimeMillis() + FLASH_MILLIS;
    changed.forEach(field -> this.flashing.put(field, until));
    refill();
    if (!changed.isEmpty()) {
      final @NotNull Set<String> names = new LinkedHashSet<>();
      changed.forEach(
          field -> {
            final @Nullable String name = author(next, field);
            if (name != null) {
              names.add(name);
            }
          });
      final @NotNull Component who =
          names.size() == 1
              ? Component.literal(names.iterator().next())
              : Component.translatable("taerra.settings.someone");
      timedStatus(
          Component.translatable(
              changed.size() == 1
                  ? "taerra.settings.live_changed.one"
                  : "taerra.settings.live_changed.other",
              who,
              changed.size()),
          ACCENT);
    }
    updateStatus();
  }

  private void arrange(final @NotNull List<String> order) {
    if (this.view == null) {
      return;
    }
    final @NotNull Set<String> known = new LinkedHashSet<>();
    this.view.worlds().forEach(world -> known.add(world.id()));
    final @NotNull List<String> wanted = order.stream().filter(known::contains).toList();
    final @NotNull List<String> queue = new ArrayList<>(wanted);
    final @NotNull LinkedHashSet<String> next = new LinkedHashSet<>();
    for (final @NotNull String id : this.display) {
      if (known.contains(id)) {
        next.add(wanted.contains(id) ? queue.removeFirst() : id);
      }
    }
    next.addAll(queue);
    next.addAll(known);
    this.display.clear();
    this.display.addAll(next);
  }

  private void fixOrder() {
    this.draft.put(
        ORDER,
        array(
            this.display.stream()
                .filter(id -> on(this.draft.get(worldField(id, "enabled"))))
                .toList()));
  }

  private void set(final @NotNull String field, final @NotNull JsonElement value) {
    this.draft.put(field, value);
    this.conflicts.remove(field);
    disarm();
    updateStatus();
  }

  private void refill() {
    this.rowsWidth = this.width;
    final @Nullable GuiEventListener focus = focusedLeaf();
    @Nullable String field = null;
    int cursor = 0;
    if (focus instanceof final @NotNull EditBox box) {
      for (final Map.@NotNull Entry<String, EditBox> entry : this.boxes.entrySet()) {
        if (entry.getValue() == box) {
          field = entry.getKey();
          cursor = box.getCursorPosition();
        }
      }
    }
    this.boxes.clear();
    for (final @NotNull SectionTab tab : this.tabs) {
      tab.list.fill(tab.section);
    }
    final @Nullable EditBox box = field == null ? null : this.boxes.get(field);
    if (box == null) {
      return;
    }
    for (final @NotNull SectionTab tab : this.tabs) {
      for (final @NotNull Row row : tab.list.children()) {
        if (row.children().contains(box)) {
          setFocused(tab.list);
          tab.list.setFocused(row);
          row.setFocused(box);
          box.setCursorPosition(Math.min(cursor, box.getValue().length()));
          box.setHighlightPos(box.getCursorPosition());
        }
      }
    }
  }

  private @Nullable GuiEventListener focusedLeaf() {
    @Nullable GuiEventListener focus = getFocused();
    while (focus instanceof final @NotNull ContainerEventHandler container
        && container.getFocused() != null) {
      focus = container.getFocused();
    }
    return focus;
  }

  private ServerSettings.@Nullable SettingInfo setting(final @NotNull String key) {
    return this.view == null
        ? null
        : this.view.settings().stream()
            .filter(setting -> setting.key().equals(key))
            .findFirst()
            .orElse(null);
  }

  private ServerSettings.@Nullable WorldInfo world(final @NotNull String id) {
    return this.view == null
        ? null
        : this.view.worlds().stream()
            .filter(world -> world.id().equals(id))
            .findFirst()
            .orElse(null);
  }

  private @NotNull String worldName(final @NotNull String id) {
    final @NotNull String name = string(this.draft.get(worldField(id, "name")));
    final ServerSettings.@Nullable WorldInfo world = world(id);
    return !name.isBlank() ? name : world == null ? id : world.defaultName();
  }

  private @NotNull Component label(final @NotNull String field) {
    if (field.equals(ORDER)) {
      return Component.translatable("taerra.settings.order");
    }
    if (field.startsWith("world:")) {
      final @NotNull String id = field.substring("world:".length(), field.lastIndexOf(':'));
      return Component.literal(worldName(id) + ": ")
          .append(
              Component.translatable(
                  "taerra.settings.world." + field.substring(field.lastIndexOf(':') + 1)));
    }
    if (field.startsWith("layer-icon:")) {
      return label(layerField(field.substring("layer-icon:".length())))
          .copy()
          .append(": ")
          .append(Component.translatable("taerra.settings.layer_icon"));
    }
    if (field.startsWith("layer-scale:")) {
      return label(layerField(field.substring("layer-scale:".length())))
          .copy()
          .append(": ")
          .append(Component.translatable("taerra.settings.layer_scale"));
    }
    if (field.startsWith("layer:")) {
      final @NotNull String id = field.substring("layer:".length());
      final @NotNull String label =
          this.view == null
              ? id
              : this.view.layers().stream()
                  .filter(layer -> layer.id().equals(id))
                  .map(ServerSettings.LayerInfo::label)
                  .findFirst()
                  .orElse(id);
      return text("taerra.layer." + id, label);
    }
    final @NotNull String key =
        field.startsWith("setting:") ? field.substring("setting:".length()) : field;
    return text("taerra.settings." + key, key);
  }

  private @NotNull Component format(
      final @NotNull String field, final @Nullable JsonElement value) {
    if (field.equals(ORDER)) {
      return Component.literal(
          String.join(" → ", ids(value).stream().map(this::worldName).toList()));
    }
    if (isLimit(field)) {
      return describeLimit(value);
    }
    if (isViews(field)) {
      return Component.translatable("taerra.settings.views." + viewsOf(value));
    }
    if (field.startsWith("layer-icon:")) {
      final @Nullable MapIcon icon = this.map.icon(string(value));
      return icon == null ? Component.translatable("taerra.icons.default") : icon.displayName();
    }
    if (field.startsWith("layer-scale:")) {
      return describeScale(parseScale(string(value)));
    }
    if (value == null || value.isJsonNull() || string(value).isEmpty()) {
      return Component.translatable("taerra.settings.default");
    }
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
      return Component.translatable(
          value.getAsBoolean() ? "taerra.settings.on" : "taerra.settings.off");
    }
    return Component.literal(
        value.getAsJsonPrimitive().isNumber()
            ? value.getAsString()
            : "“" + value.getAsString() + "”");
  }

  private @Nullable Component changedBy(final @NotNull String field) {
    final ServerSettings.@Nullable Author author =
        this.view == null || this.view.authors() == null ? null : this.view.authors().get(field);
    return author == null || author.name() == null
        ? null
        : Component.translatable("taerra.settings.changed_by", author.name(), since(author.at()));
  }

  private @NotNull JsonObject changes() {
    final @NotNull JsonObject changes = new JsonObject();
    this.draft.forEach(
        (field, value) -> {
          if (field.equals(ORDER)) {
            if (!same(value, this.base.get(ORDER))) {
              changes.add(ORDER, value);
            }
            return;
          }
          if (same(value, this.base.get(field))) {
            return;
          }
          final ServerSettings.@Nullable SettingInfo setting =
              field.startsWith("setting:") ? setting(field.substring("setting:".length())) : null;
          if (setting == null || setting.editable()) {
            changes.add(field, value);
          }
        });
    return changes;
  }

  private @Nullable Component problem(final @NotNull JsonObject changes) {
    for (final @NotNull String field : changes.keySet()) {
      final ServerSettings.@Nullable SettingInfo setting =
          field.startsWith("setting:") ? setting(field.substring("setting:".length())) : null;
      if (setting != null && outOfRange(setting, changes.get(field))) {
        return Component.translatable(
            "taerra.settings.range",
            label(field),
            String.valueOf(setting.min()),
            String.valueOf(setting.max()));
      }
    }
    final @NotNull List<String> limits =
        changes.keySet().stream().filter(ServerSettingsScreen::isLimit).toList();
    for (final @NotNull String field : limits) {
      if (badLimit(changes.get(field))) {
        return Component.translatable(
            "taerra.settings.limit_invalid",
            worldName(field.substring("world:".length(), field.lastIndexOf(':'))));
      }
    }
    final long renders =
        limits.size()
            + changes.keySet().stream()
                .filter(
                    field ->
                        isViews(field)
                            && on(
                                this.draft.get(
                                    worldField(
                                        field.substring("world:".length(), field.lastIndexOf(':')),
                                        "enabled")))
                            && addsView(this.base.get(field), changes.get(field)))
                .count();
    return renders > 1 ? Component.translatable("taerra.settings.limit_one") : null;
  }

  private void updateStatus() {
    final @NotNull JsonObject pending = changes();
    final int count =
        pending.size()
            - (pending.has(ORDER) && sameOrder(pending.get(ORDER), this.base.get(ORDER)) ? 1 : 0);
    final @Nullable Component problem = problem(pending);
    if (this.save != null) {
      this.save.active = count > 0 && this.conflicts.isEmpty() && problem == null && !this.saving;
    }
    if (!this.conflicts.isEmpty()) {
      setStatus(
          Component.translatable(
              this.conflicts.size() == 1
                  ? "taerra.settings.conflicts.one"
                  : "taerra.settings.conflicts.other",
              this.conflicts.size()),
          GOLD);
    } else if (problem != null) {
      setStatus(problem, GOLD);
    } else if (count > 0) {
      setStatus(
          Component.translatable(
              count == 1 ? "taerra.settings.unsaved.one" : "taerra.settings.unsaved.other", count),
          GOLD);
    } else if (this.statusUntil == 0 && this.statusColor != RED) {
      setStatus(Component.empty(), MUTED);
    }
  }

  private void setStatus(final @NotNull Component status, final int color) {
    this.status = status;
    this.statusColor = color;
    this.statusUntil = 0;
  }

  private void timedStatus(final @NotNull Component status, final int color) {
    this.status = status;
    this.statusColor = color;
    this.statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
  }

  private void save() {
    final @NotNull JsonObject changes = changes();
    if (changes.isEmpty()
        || this.saving
        || !this.conflicts.isEmpty()
        || problem(changes) != null
        || this.view == null) {
      return;
    }
    disarm();
    final @NotNull JsonObject body = new JsonObject();
    body.addProperty("base", this.view.revision());
    body.add("changes", changes);
    final @NotNull String saved =
        changes.keySet().stream().anyMatch(ServerSettingsScreen::isLimit)
            ? "taerra.settings.saved_limit"
            : changes.keySet().stream()
                    .anyMatch(
                        field ->
                            isViews(field) && addsView(this.base.get(field), changes.get(field)))
                ? "taerra.settings.saved_views"
                : changes.keySet().stream().anyMatch(ServerSettingsScreen::isStorage)
                    ? "taerra.settings.saved_storage"
                    : "taerra.settings.saved";
    this.saving = true;
    updateStatus();
    this.map.saveSettings(
        body,
        (status, response) -> {
          this.saving = false;
          @Nullable ServerSettings next = null;
          if ((status == 200 || status == 409) && response != null) {
            try {
              next = GSON.fromJson(response, ServerSettings.class);
            } catch (final RuntimeException ignored) {
            }
          }
          if (status == 200 && next != null) {
            changes.entrySet().forEach(entry -> this.base.put(entry.getKey(), entry.getValue()));
            rebase(next);
            timedStatus(Component.translatable(saved), GREEN);
            updateStatus();
            return;
          }
          if (status == 409 && next != null) {
            rebase(next);
            final @NotNull JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (json.has("busy")) {
              setStatus(
                  Component.translatable(
                      isStorage(json.get("busy").getAsString())
                          ? "taerra.settings.storage_busy"
                          : "taerra.settings.limit_busy"),
                  RED);
            }
            return;
          }
          if (status == 400) {
            @Nullable String invalid = null;
            @Nullable String unreachable = null;
            try {
              final @NotNull JsonObject json =
                  JsonParser.parseString(Objects.requireNonNullElse(response, "{}"))
                      .getAsJsonObject();
              invalid = json.get("invalid").getAsString();
              unreachable = json.has("unreachable") ? json.get("unreachable").getAsString() : null;
            } catch (final RuntimeException ignored) {
            }
            setStatus(
                unreachable != null
                    ? Component.translatable("taerra.settings.storage_failed", unreachable)
                    : Component.translatable(
                        "taerra.settings.invalid", label(Objects.requireNonNullElse(invalid, ""))),
                RED);
          } else {
            setStatus(
                Component.translatable(
                    status == 401 || status == 403
                        ? "taerra.settings.denied"
                        : "taerra.settings.failed"),
                RED);
          }
          if (this.save != null) {
            this.save.active = true;
          }
        });
  }

  private record Section(@NotNull String id, @NotNull List<String> keys) {}

  private record Conflict(@NotNull JsonElement theirs, @Nullable String by) {}

  private final class SectionTab implements Tab {

    private final @NotNull Section section;
    private final @NotNull SettingsList list = new SettingsList();
    private final @NotNull Layout layout = LinearLayout.vertical();

    private SectionTab(final @NotNull Section section) {
      this.section = section;
    }

    @Override
    public @NotNull Component getTabTitle() {
      return Component.translatable("taerra.settings.section." + this.section.id());
    }

    @Override
    public @NotNull Component getTabExtraNarration() {
      return Component.empty();
    }

    @Override
    public void visitChildren(final @NotNull Consumer<AbstractWidget> consumer) {
      consumer.accept(this.list);
    }

    @Override
    public void doLayout(final @NotNull ScreenRectangle area) {
      this.list.updateSizeAndPosition(area.width(), area.height(), area.left(), area.top());
      ServerSettingsScreen.this.selected = ServerSettingsScreen.this.tabs.indexOf(this);
    }

    @Override
    public @NotNull Layout getLayout() {
      return this.layout;
    }
  }

  private final class SettingsList extends ContainerObjectSelectionList<Row> {

    private SettingsList() {
      super(Minecraft.getInstance(), 0, 0, 0, 30);
    }

    private void add(final @NotNull Row row) {
      addEntry(row, row.height());
    }

    private void fill(final @NotNull Section section) {
      final double scroll = scrollAmount();
      clearEntries();
      final @Nullable ServerSettings view = ServerSettingsScreen.this.view;
      if (view == null) {
        return;
      }
      final @Nullable Component intro =
          optional("taerra.settings.section." + section.id() + ".hint");
      if (intro != null) {
        add(new IntroRow(intro));
      }
      if (section.id().equals("worlds")) {
        conflict(ORDER);
        final @NotNull List<String> display = ServerSettingsScreen.this.display;
        int rank = 0;
        for (int index = 0; index < display.size(); index++) {
          final ServerSettings.@Nullable WorldInfo world = world(display.get(index));
          if (world == null) {
            continue;
          }
          final boolean on =
              on(ServerSettingsScreen.this.draft.get(worldField(world.id(), "enabled")));
          add(new WorldRow(world, index, display.size(), on ? ++rank : 0));
          for (final @NotNull String part : List.of("enabled", "private", "players", "name")) {
            conflict(worldField(world.id(), part));
          }
        }
      }
      if (section.id().equals("limits")) {
        for (final @NotNull String id : ServerSettingsScreen.this.display) {
          final ServerSettings.@Nullable WorldInfo world = world(id);
          if (world != null && on(ServerSettingsScreen.this.draft.get(worldField(id, "enabled")))) {
            add(new LimitRow(world));
            conflict(worldField(id, "limit"));
          }
        }
      }
      if (section.id().equals("views")) {
        for (final @NotNull String id : ServerSettingsScreen.this.display) {
          final ServerSettings.@Nullable WorldInfo world = world(id);
          if (world != null && on(ServerSettingsScreen.this.draft.get(worldField(id, "enabled")))) {
            add(new ViewsRow(world));
            conflict(worldField(id, "views"));
          }
        }
      }
      for (final @NotNull String key : section.keys()) {
        final ServerSettings.@Nullable SettingInfo setting = setting(key);
        if (setting != null) {
          add(new SettingRow(setting));
          conflict(settingField(key));
        }
      }
      if (section.id().equals("layers") && !view.layers().isEmpty()) {
        add(new HeadingRow(Component.translatable("taerra.settings.private_layers")));
        for (final ServerSettings.@NotNull LayerInfo layer : view.layers()) {
          add(new LayerRow(layer));
          conflict(layerField(layer.id()));
          conflict(layerIconField(layer.id()));
          conflict(layerScaleField(layer.id()));
        }
      }
      setScrollAmount(scroll);
    }

    private void conflict(final @NotNull String field) {
      final @Nullable Conflict conflict = ServerSettingsScreen.this.conflicts.get(field);
      if (conflict != null) {
        add(new ConflictRow(field, conflict));
      }
    }

    @Override
    public int getRowWidth() {
      return rowWidth();
    }
  }

  private abstract class Row extends ContainerObjectSelectionList.Entry<Row> {

    abstract int height();

    abstract @NotNull List<String> fields();

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return List.of();
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return List.of();
    }

    void slab(
        final @NotNull GuiGraphicsExtractor graphics,
        final int indent,
        final boolean hovered,
        final int mouseX,
        final int mouseY) {
      final int left = getX() + indent;
      final int top = getY() + 1;
      final int right = getX() + getWidth();
      final int bottom = getY() + getHeight() - 1;
      graphics.fill(left, top, right, bottom, hovered ? SLAB_HOVER : SLAB);
      final long now = System.currentTimeMillis();
      final long until =
          fields().stream()
              .mapToLong(field -> ServerSettingsScreen.this.flashing.getOrDefault(field, 0L))
              .max()
              .orElse(0L);
      if (until > now) {
        final int alpha = (int) (0x70 * Math.min(1.0, (until - now) / (double) FLASH_MILLIS * 1.6));
        graphics.fill(left, top, right, bottom, (alpha << 24) | 0x5E2A0A);
      }
      if (fields().stream().anyMatch(ServerSettingsScreen.this.conflicts::containsKey)) {
        graphics.outline(left, top, right - left, bottom - top, GOLD);
      }
      if (fields().stream()
          .anyMatch(
              field ->
                  !same(
                      ServerSettingsScreen.this.draft.get(field),
                      ServerSettingsScreen.this.base.get(field)))) {
        graphics.fill(right - 2, top, right, bottom, ACCENT);
      }
      if (hovered) {
        for (final @NotNull String field : fields()) {
          final @Nullable Component changed = changedBy(field);
          if (changed != null) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, changed, mouseX, mouseY);
            break;
          }
        }
      }
    }
  }

  private final class IntroRow extends Row {

    private final @NotNull List<FormattedCharSequence> lines;

    private IntroRow(final @NotNull Component text) {
      this.lines = Minecraft.getInstance().font.split(text, rowWidth() - 8);
    }

    @Override
    int height() {
      return this.lines.size() * 10 + 10;
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of();
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      final @NotNull Font font = Minecraft.getInstance().font;
      for (int i = 0; i < this.lines.size(); i++) {
        graphics.text(
            font, this.lines.get(i), getContentX(), getContentY() + 2 + i * 10, MUTED, false);
      }
    }
  }

  private final class HeadingRow extends Row {

    private final @NotNull Component text;

    private HeadingRow(final @NotNull Component text) {
      this.text = text.copy().withStyle(ChatFormatting.BOLD);
    }

    @Override
    int height() {
      return 22;
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of();
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      graphics.text(
          Minecraft.getInstance().font, this.text, getContentX(), getContentYMiddle() - 2, ACCENT);
    }
  }

  private final class ConflictRow extends Row {

    private final @NotNull String field;
    private final @NotNull List<FormattedCharSequence> lines;
    private final @NotNull Button theirs;
    private final @NotNull Button mine;

    private ConflictRow(final @NotNull String field, final @NotNull Conflict conflict) {
      this.field = field;
      final @NotNull Component who =
          conflict.by() == null
              ? Component.translatable("taerra.settings.someone")
              : Component.literal(conflict.by());
      this.lines =
          Minecraft.getInstance()
              .font
              .split(
                  Component.translatable(
                      "taerra.settings.conflict_named",
                      who,
                      label(field),
                      format(field, conflict.theirs())),
                  rowWidth() - 140);
      this.theirs =
          Button.builder(
                  Component.translatable("taerra.settings.use_theirs"),
                  _ -> {
                    ServerSettingsScreen.this.draft.put(field, conflict.theirs().deepCopy());
                    ServerSettingsScreen.this.conflicts.remove(field);
                    if (field.equals(ORDER)) {
                      arrange(ids(conflict.theirs()));
                    }
                    if (field.equals(ORDER) || field.endsWith(":enabled")) {
                      fixOrder();
                    }
                    refill();
                    updateStatus();
                  })
              .size(64, 18)
              .build();
      this.mine =
          Button.builder(
                  Component.translatable("taerra.settings.keep_mine"),
                  _ -> {
                    ServerSettingsScreen.this.conflicts.remove(field);
                    refill();
                    updateStatus();
                  })
              .size(64, 18)
              .build();
    }

    @Override
    int height() {
      return Math.max(26, this.lines.size() * 10 + 8);
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(this.field);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return List.of(this.theirs, this.mine);
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return List.of(this.theirs, this.mine);
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight() - 1, 0x33FFCF3F);
      graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight() - 1, GOLD);
      final @NotNull Font font = Minecraft.getInstance().font;
      final int top = getY() + getHeight() / 2 - this.lines.size() * 5;
      for (int i = 0; i < this.lines.size(); i++) {
        graphics.text(font, this.lines.get(i), getX() + 8, top + i * 10 + 1, 0xFFD0D1D4, false);
      }
      final int middle = getY() + getHeight() / 2 - 9;
      this.mine.setPosition(getX() + getWidth() - 4 - this.mine.getWidth(), middle);
      this.theirs.setPosition(this.mine.getX() - 4 - this.theirs.getWidth(), middle);
      this.theirs.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.mine.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  private final class SettingRow extends Row {

    private final ServerSettings.@NotNull SettingInfo setting;
    private final @NotNull String field;
    private final @NotNull Component label;
    private final @NotNull List<FormattedCharSequence> hint;
    private final @Nullable AbstractWidget control;
    private final @Nullable Component unit;
    private final @Nullable String parent;
    private final boolean database;

    private SettingRow(final ServerSettings.@NotNull SettingInfo setting) {
      this.setting = setting;
      this.database = setting.key().startsWith("storage.database.");
      this.field = settingField(setting.key());
      this.parent =
          setting.key().endsWith(".shown-by-default")
              ? settingField(setting.key().replaceFirst("shown-by-default$", "enabled"))
              : null;
      this.label = label(this.field);
      final @NotNull Font font = Minecraft.getInstance().font;
      final @Nullable Component hintText =
          setting.editable()
              ? optional("taerra.settings." + setting.key() + ".hint")
              : Component.translatable(
                  Language.getInstance().has("taerra.settings." + setting.key() + ".locked")
                      ? "taerra.settings." + setting.key() + ".locked"
                      : "taerra.settings.read_only",
                  "/taerra config " + setting.key());
      this.unit = optional("taerra.settings." + setting.key() + ".unit");
      this.control = control(font);
      final int controlWidth =
          Math.max(CONTROL_WIDTH, this.control == null ? 0 : this.control.getWidth());
      this.hint =
          hintText == null
              ? List.of()
              : font.split(hintText, rowWidth() - controlWidth - 24 - (sub() ? INDENT : 0));
    }

    private @Nullable AbstractWidget control(final @NotNull Font font) {
      final @Nullable JsonElement value = ServerSettingsScreen.this.draft.get(this.field);
      if (!this.setting.editable()) {
        return null;
      }
      return switch (this.setting.kind()) {
        case "toggle" ->
            CycleButton.onOffBuilder(on(value))
                .displayOnlyValue()
                .create(
                    0,
                    0,
                    60,
                    20,
                    this.label,
                    (_, on) -> {
                      set(this.field, new JsonPrimitive(on));
                      refill();
                    });
        case "choice" ->
            CycleButton.builder(
                    (String option) ->
                        Component.translatable(
                            "taerra.settings." + this.setting.key() + "." + option),
                    string(value))
                .withValues(Objects.requireNonNullElse(this.setting.options(), List.of()))
                .displayOnlyValue()
                .create(
                    0,
                    0,
                    100,
                    20,
                    this.label,
                    (_, option) -> {
                      set(this.field, new JsonPrimitive(option));
                      refill();
                    });
        case "secret" -> {
          final @Nullable JsonElement stored = ServerSettingsScreen.this.base.get(this.field);
          final @NotNull EditBox box = new EditBox(font, CONTROL_WIDTH, 18, this.label);
          box.setMaxLength(256);
          box.setValue(same(value, stored) ? "" : string(value));
          box.setHint(
              (string(stored).isEmpty()
                      ? Component.translatable("taerra.settings.not_set")
                      : Component.literal("********"))
                  .withStyle(ChatFormatting.DARK_GRAY));
          box.addFormatter(
              (text, _) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
          box.setResponder(
              text ->
                  set(
                      this.field,
                      text.isEmpty()
                          ? Objects.requireNonNullElse(stored, new JsonPrimitive(""))
                          : new JsonPrimitive(text)));
          ServerSettingsScreen.this.boxes.put(this.field, box);
          yield box;
        }
        case "number" -> {
          final @NotNull EditBox box = new EditBox(font, 64, 18, this.label);
          box.setMaxLength(9);
          box.setValue(value == null || value.isJsonNull() ? "" : value.getAsString());
          clean(
              box,
              NOT_DIGIT,
              text -> {
                set(
                    this.field,
                    text.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(Integer.parseInt(text)));
                box.setTextColor(invalidNow() ? RED : TEXT);
              });
          box.setTextColor(invalidNow() ? RED : TEXT);
          ServerSettingsScreen.this.boxes.put(this.field, box);
          yield box;
        }
        default -> {
          final boolean name = this.setting.key().equals("web.name");
          final @NotNull Component placeholder =
              Component.literal(
                      name
                          ? "TAERRA"
                          : this.setting.key().equals("web.public-url")
                              ? "https://map.example.com"
                              : "")
                  .withStyle(ChatFormatting.DARK_GRAY);
          final @NotNull EditBox box =
              new EditBox(
                  font, Math.max(CONTROL_WIDTH, font.width(placeholder) + 10), 18, this.label);
          box.setMaxLength(name ? 32 : 256);
          box.setValue(string(value));
          box.setHint(placeholder);
          final @NotNull Consumer<String> changed =
              text -> set(this.field, new JsonPrimitive(text));
          if (name) {
            clean(box, NOT_NAME, changed);
          } else {
            box.setResponder(changed);
          }
          ServerSettingsScreen.this.boxes.put(this.field, box);
          yield box;
        }
      };
    }

    private boolean sub() {
      return this.parent != null || this.database;
    }

    private boolean invalidNow() {
      final @Nullable JsonElement value = ServerSettingsScreen.this.draft.get(this.field);
      return !same(value, ServerSettingsScreen.this.base.get(this.field))
          && outOfRange(this.setting, value);
    }

    @Override
    int height() {
      return Math.max(30, 22 + this.hint.size() * 10);
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(this.field);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return this.control == null ? List.of() : List.of(this.control);
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return this.control == null ? List.of() : List.of(this.control);
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      final int indent = sub() ? INDENT : 0;
      final boolean inactive =
          this.parent != null && !on(ServerSettingsScreen.this.draft.get(this.parent))
              || this.database
                  && "files".equals(string(ServerSettingsScreen.this.draft.get(STORAGE_TYPE)));
      if (this.control != null) {
        this.control.active = !inactive;
      }
      slab(graphics, indent, hovered && this.control != null && !inactive, mouseX, mouseY);
      if (sub()) {
        graphics.fill(
            getX() + indent, getY() + 1, getX() + indent + 2, getY() + getHeight() - 1, 0xFF48494A);
      }
      final @NotNull Font font = Minecraft.getInstance().font;
      final int top = getY() + 6;
      final int left = getContentX() + 4 + indent;
      graphics.text(
          font, this.label, left, top, this.control == null || inactive ? MUTED : 0xFFFFFFFF, true);
      for (int i = 0; i < this.hint.size(); i++) {
        graphics.text(
            font, this.hint.get(i), left, top + 11 + i * 10, inactive ? 0xFF6E6E70 : MUTED, false);
      }
      final int right = getContentRight() - 6;
      final int middle = getY() + getHeight() / 2;
      if (this.control == null) {
        final @NotNull String value = string(ServerSettingsScreen.this.draft.get(this.field));
        final @NotNull Component shown =
            value.isEmpty()
                ? Component.translatable("taerra.settings.not_set").withStyle(ChatFormatting.ITALIC)
                : Component.literal(
                    font.width(value) <= CONTROL_WIDTH
                        ? value
                        : font.plainSubstrByWidth(value, CONTROL_WIDTH - font.width("…")) + "…");
        graphics.text(font, shown, right - font.width(shown), middle - 4, MUTED, false);
        return;
      }
      int controlRight = right;
      if (this.unit != null) {
        controlRight = right - UNIT_WIDTH;
        graphics.text(font, this.unit, controlRight + 6, middle - 4, MUTED, false);
      }
      this.control.setPosition(
          controlRight - this.control.getWidth(), middle - this.control.getHeight() / 2);
      this.control.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  private final class WorldRow extends Row {

    private final ServerSettings.@NotNull WorldInfo world;
    private final int index;
    private final int rank;
    private final @NotNull EditBox name;
    private final @NotNull CycleButton<Boolean> enabled;
    private final @NotNull CycleButton<Boolean> restricted;
    private final @NotNull CycleButton<Boolean> players;
    private final @NotNull Button up;
    private final @NotNull Button down;

    private WorldRow(
        final ServerSettings.@NotNull WorldInfo world,
        final int index,
        final int count,
        final int rank) {
      this.world = world;
      this.index = index;
      this.rank = rank;
      final @NotNull Font font = Minecraft.getInstance().font;
      final @NotNull String nameField = worldField(world.id(), "name");
      this.name =
          new EditBox(
              font,
              Math.clamp(rowWidth() - 238, 60, 130),
              18,
              Component.translatable("taerra.settings.world_name", world.defaultName()));
      this.name.setMaxLength(32);
      this.name.setValue(string(ServerSettingsScreen.this.draft.get(nameField)));
      this.name.setHint(Component.literal(world.defaultName()).withStyle(ChatFormatting.DARK_GRAY));
      clean(this.name, NOT_NAME, text -> set(nameField, new JsonPrimitive(text)));
      ServerSettingsScreen.this.boxes.put(nameField, this.name);
      final @NotNull String enabledField = worldField(world.id(), "enabled");
      this.enabled =
          CycleButton.booleanBuilder(
                  Component.translatable("taerra.settings.on_map").withStyle(ChatFormatting.GREEN),
                  Component.translatable("taerra.settings.off_map").withStyle(ChatFormatting.GRAY),
                  on(ServerSettingsScreen.this.draft.get(enabledField)))
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  78,
                  20,
                  Component.translatable("taerra.settings.on_map"),
                  (_, on) -> {
                    set(enabledField, new JsonPrimitive(on));
                    fixOrder();
                    refill();
                  });
      final @NotNull String privateField = worldField(world.id(), "private");
      this.restricted =
          CycleButton.booleanBuilder(
                  Component.translatable("taerra.settings.private").withStyle(ChatFormatting.GOLD),
                  Component.translatable("taerra.settings.public"),
                  on(ServerSettingsScreen.this.draft.get(privateField)))
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  80,
                  20,
                  Component.translatable("taerra.settings.private"),
                  (_, on) -> {
                    set(privateField, new JsonPrimitive(on));
                    refill();
                  });
      final @NotNull String playersField = worldField(world.id(), "players");
      this.players =
          CycleButton.booleanBuilder(
                  Component.translatable("taerra.settings.players_private")
                      .withStyle(ChatFormatting.GOLD),
                  Component.translatable("taerra.settings.players_public"),
                  on(ServerSettingsScreen.this.draft.get(playersField)))
              .displayOnlyValue()
              .withTooltip(
                  _ ->
                      Tooltip.create(
                          Component.translatable(
                              "taerra.settings.players_hint",
                              "taerra.players." + world.id().replace(':', '.'))))
              .create(
                  0,
                  0,
                  80,
                  20,
                  Component.translatable("taerra.settings.players_private"),
                  (_, on) -> {
                    set(playersField, new JsonPrimitive(on));
                    refill();
                  });
      this.up =
          Button.builder(Component.literal("▲"), _ -> move(-1))
              .size(16, 20)
              .tooltip(Tooltip.create(Component.translatable("taerra.settings.move.up")))
              .build();
      this.down =
          Button.builder(Component.literal("▼"), _ -> move(1))
              .size(16, 20)
              .tooltip(Tooltip.create(Component.translatable("taerra.settings.move.down")))
              .build();
      this.up.active = index > 0;
      this.down.active = index < count - 1;
    }

    private void move(final int by) {
      final @NotNull List<String> display = ServerSettingsScreen.this.display;
      final int target = this.index + by;
      display.set(this.index, display.set(target, display.get(this.index)));
      fixOrder();
      ServerSettingsScreen.this.conflicts.remove(ORDER);
      updateStatus();
      refill();
    }

    @Override
    int height() {
      return 46;
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(
          worldField(this.world.id(), "enabled"),
          worldField(this.world.id(), "private"),
          worldField(this.world.id(), "players"),
          worldField(this.world.id(), "name"));
    }

    private @NotNull List<AbstractWidget> widgets() {
      return List.of(this.name, this.enabled, this.restricted, this.players, this.up, this.down);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return widgets();
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return widgets();
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      slab(graphics, 0, hovered, mouseX, mouseY);
      final @NotNull Font font = Minecraft.getInstance().font;
      final int color = environmentColor(this.world.environment());
      graphics.fill(getX(), getY() + 1, getX() + 2, getY() + getHeight() - 1, color);
      final boolean on = this.rank > 0;
      graphics.centeredText(
          font,
          on ? String.valueOf(this.rank) : "–",
          getX() + 12,
          getY() + getHeight() / 2 - 4,
          on ? 0xFFFFFFFF : MUTED);
      final int left = getX() + 24;
      this.name.setPosition(left, getY() + 5);
      this.name.extractRenderState(graphics, mouseX, mouseY, partialTick);
      final @NotNull Component environment =
          this.world.environment() == null
              ? Component.translatable("taerra.settings.not_loaded")
              : Component.translatable("taerra.env." + this.world.environment());
      final @NotNull Component about =
          Component.empty()
              .append(environment)
              .append(
                  Component.literal("  " + this.world.id()).withStyle(ChatFormatting.DARK_GRAY));
      final int room = getX() + getWidth() - 6 - 200 - left - 4;
      graphics.text(
          font,
          font.width(about) <= room ? about : environment,
          left + 1,
          getY() + 28,
          color,
          false);
      int right = getX() + getWidth() - 6;
      for (final @NotNull AbstractWidget widget :
          List.<AbstractWidget>of(this.down, this.up, this.restricted, this.enabled)) {
        right -= widget.getWidth();
        if (widget == this.restricted) {
          this.restricted.setPosition(right, getY() + 2);
          this.players.setPosition(right, getY() + 24);
          this.players.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } else {
          widget.setPosition(right, getY() + getHeight() / 2 - widget.getHeight() / 2);
        }
        widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        right -= widget == this.up ? 6 : 2;
      }
    }
  }

  private final class LimitRow extends Row {

    private final ServerSettings.@NotNull WorldInfo world;
    private final @NotNull String field;
    private final @NotNull CycleButton<String> shape;
    private final @NotNull List<EditBox> inputs = new ArrayList<>();
    private final @NotNull List<String> labels = new ArrayList<>();

    private LimitRow(final ServerSettings.@NotNull WorldInfo world) {
      this.world = world;
      this.field = worldField(world.id(), "limit");
      final @Nullable JsonElement value = ServerSettingsScreen.this.draft.get(this.field);
      final @NotNull String current = shapeOf(value);
      final @NotNull Font font = Minecraft.getInstance().font;
      this.shape =
          CycleButton.builder(
                  (String shape) -> Component.translatable("taerra.settings.limit." + shape),
                  current)
              .withValues(SHAPES)
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  100,
                  20,
                  Component.translatable("taerra.settings.limit"),
                  (_, shape) -> {
                    set(
                        this.field,
                        reshape(ServerSettingsScreen.this.draft.get(this.field), shape));
                    refill();
                  });
      if (current.equals("none")) {
        return;
      }
      final boolean round = !current.equals("rectangle");
      final @Nullable Integer minX = bound(value, "minX");
      final @Nullable Integer minZ = bound(value, "minZ");
      final @Nullable Integer maxX = bound(value, "maxX");
      final @Nullable Integer maxZ = bound(value, "maxZ");
      final @NotNull List<String> keys =
          round ? List.of("x", "z", "radius") : List.of("from_x", "from_z", "to_x", "to_z");
      final @Nullable Integer[] numbers =
          round
              ? new Integer[] {
                minX == null || maxX == null ? null : Math.floorDiv(minX + maxX, 2),
                minZ == null || maxZ == null ? null : Math.floorDiv(minZ + maxZ, 2),
                minX == null || maxX == null ? null : (maxX - minX) / 2
              }
              : new Integer[] {minX, minZ, maxX, maxZ};
      final @NotNull List<String> marks =
          round ? List.of("X", "Z", "R") : List.of("X", "Z", "→ X", "Z");
      final int labels = marks.stream().mapToInt(mark -> font.width(mark) + 4).sum();
      final int width =
          Math.clamp((rowWidth() - 8 - labels - 10 * (keys.size() - 1)) / keys.size(), 36, 64);
      for (int i = 0; i < keys.size(); i++) {
        final @NotNull Component name =
            Component.translatable("taerra.settings.limit." + keys.get(i));
        final @NotNull EditBox box = new EditBox(font, width, 18, name);
        box.setMaxLength(9);
        box.setValue(numbers[i] == null ? "" : String.valueOf(numbers[i]));
        box.setTooltip(Tooltip.create(name));
        ServerSettingsScreen.this.boxes.put(this.field + ":" + keys.get(i), box);
        this.inputs.add(box);
        this.labels.add(marks.get(i));
      }
      this.inputs.forEach(box -> clean(box, NOT_COORDINATE, _ -> update(current, round)));
      paint();
    }

    private void update(final @NotNull String current, final boolean round) {
      final @Nullable Integer[] n =
          this.inputs.stream().map(box -> coordinate(box.getValue())).toArray(Integer[]::new);
      set(
          this.field,
          round
              ? limit(
                  current,
                  n[0] == null || n[2] == null ? null : n[0] - n[2],
                  n[1] == null || n[2] == null ? null : n[1] - n[2],
                  n[0] == null || n[2] == null ? null : n[0] + n[2],
                  n[1] == null || n[2] == null ? null : n[1] + n[2])
              : limit(
                  current,
                  n[0] == null || n[2] == null ? null : Math.min(n[0], n[2]),
                  n[1] == null || n[3] == null ? null : Math.min(n[1], n[3]),
                  n[0] == null || n[2] == null ? null : Math.max(n[0], n[2]),
                  n[1] == null || n[3] == null ? null : Math.max(n[1], n[3])));
      paint();
    }

    private boolean changed() {
      return !same(
          ServerSettingsScreen.this.draft.get(this.field),
          ServerSettingsScreen.this.base.get(this.field));
    }

    private void paint() {
      final boolean bad = changed() && badLimit(ServerSettingsScreen.this.draft.get(this.field));
      this.inputs.forEach(box -> box.setTextColor(bad ? RED : TEXT));
    }

    private @NotNull List<AbstractWidget> widgets() {
      final @NotNull List<AbstractWidget> widgets = new ArrayList<>();
      widgets.add(this.shape);
      widgets.addAll(this.inputs);
      return widgets;
    }

    @Override
    int height() {
      return this.inputs.isEmpty() ? 30 : 54;
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(this.field);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return widgets();
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return widgets();
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      slab(graphics, 0, hovered, mouseX, mouseY);
      final @NotNull Font font = Minecraft.getInstance().font;
      final int left = getContentX() + 4;
      final int room = getContentRight() - 6 - this.shape.getWidth() - 8 - left;
      final @NotNull String name = worldName(this.world.id());
      graphics.text(
          font,
          font.width(name) <= room
              ? name
              : font.plainSubstrByWidth(name, room - font.width("…")) + "…",
          left,
          getY() + 6,
          0xFFFFFFFF,
          true);
      graphics.text(font, this.world.id(), left, getY() + 17, MUTED, false);
      this.shape.setPosition(getContentRight() - 6 - this.shape.getWidth(), getY() + 5);
      this.shape.extractRenderState(graphics, mouseX, mouseY, partialTick);
      int x = left;
      for (int i = 0; i < this.inputs.size(); i++) {
        final @NotNull String label = this.labels.get(i);
        graphics.text(font, label, x, getY() + 36, MUTED, false);
        x += font.width(label) + 4;
        final @NotNull EditBox box = this.inputs.get(i);
        box.setPosition(x, getY() + 31);
        box.extractRenderState(graphics, mouseX, mouseY, partialTick);
        x += box.getWidth() + 10;
      }
    }
  }

  private final class ViewsRow extends Row {

    private final ServerSettings.@NotNull WorldInfo world;
    private final @NotNull String field;
    private final @NotNull CycleButton<String> views;
    private final @NotNull List<FormattedCharSequence> effect;

    private ViewsRow(final ServerSettings.@NotNull WorldInfo world) {
      this.world = world;
      this.field = worldField(world.id(), "views");
      final @Nullable JsonElement before = ServerSettingsScreen.this.base.get(this.field);
      final @NotNull String current = viewsOf(ServerSettingsScreen.this.draft.get(this.field));
      this.views =
          CycleButton.builder(
                  (String views) -> Component.translatable("taerra.settings.views." + views),
                  current)
              .withValues(VIEWS)
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  CONTROL_WIDTH,
                  20,
                  Component.translatable("taerra.settings.world.views"),
                  (_, views) -> {
                    set(this.field, new JsonPrimitive(views));
                    refill();
                  });
      final @NotNull MutableComponent effect = Component.empty();
      for (final @NotNull String view : partsOf(viewsOf(before))) {
        if (!partsOf(current).contains(view)) {
          effect.append(Component.translatable("taerra.settings.views.drops." + view)).append(" ");
        }
      }
      if (addsView(before, new JsonPrimitive(current))) {
        effect.append(Component.translatable("taerra.settings.views.renders"));
      }
      this.effect =
          effect.getString().isEmpty()
              ? List.of()
              : Minecraft.getInstance().font.split(effect, rowWidth() - 12);
    }

    @Override
    int height() {
      return this.effect.isEmpty() ? 30 : 32 + this.effect.size() * 10;
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(this.field);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return List.of(this.views);
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return List.of(this.views);
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      slab(graphics, 0, hovered, mouseX, mouseY);
      final @NotNull Font font = Minecraft.getInstance().font;
      final int left = getContentX() + 4;
      final int room = getContentRight() - 6 - this.views.getWidth() - 8 - left;
      final @NotNull String name = worldName(this.world.id());
      graphics.text(
          font,
          font.width(name) <= room
              ? name
              : font.plainSubstrByWidth(name, room - font.width("…")) + "…",
          left,
          getY() + 6,
          0xFFFFFFFF,
          true);
      graphics.text(font, this.world.id(), left, getY() + 17, MUTED, false);
      for (int i = 0; i < this.effect.size(); i++) {
        graphics.text(font, this.effect.get(i), left, getY() + 30 + i * 10, ACCENT, false);
      }
      this.views.setPosition(getContentRight() - 6 - this.views.getWidth(), getY() + 5);
      this.views.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  private final class LayerRow extends Row {

    private final @NotNull String field;
    private final @NotNull String iconField;
    private final @NotNull Component label;
    private final @NotNull List<FormattedCharSequence> hint;
    private final @NotNull CycleButton<Boolean> toggle;
    private final @NotNull IconChoice icon;
    private final @NotNull String scaleField;
    private final @NotNull ScaleSlider scale;

    private LayerRow(final ServerSettings.@NotNull LayerInfo layer) {
      this.field = layerField(layer.id());
      this.iconField = layerIconField(layer.id());
      this.scaleField = layerScaleField(layer.id());
      this.scale = new ScaleSlider(this.scaleField);
      this.label = label(this.field);
      final @NotNull WebMap map = ServerSettingsScreen.this.map;
      final @NotNull String chosen = string(ServerSettingsScreen.this.draft.get(this.iconField));
      this.icon =
          new IconChoice(
              20,
              label(this.iconField)
                  .copy()
                  .append(": ")
                  .append(format(this.iconField, new JsonPrimitive(chosen))),
              () -> MapIcons.icon(map, chosen, false),
              () ->
                  ServerSettingsScreen.this.minecraft.setScreenAndShow(
                      new IconPickerScreen(
                          ServerSettingsScreen.this,
                          map,
                          Component.translatable("taerra.settings.layer_icon_title", this.label),
                          chosen,
                          () -> MapIcons.icon(map, null, false),
                          Component.translatable("taerra.icons.default"),
                          picked -> {
                            set(this.iconField, new JsonPrimitive(picked));
                            refill();
                          })));
      this.icon.setTooltip(Tooltip.create(this.icon.getMessage()));
      this.hint =
          Minecraft.getInstance()
              .font
              .split(
                  layer.present()
                      ? Component.translatable(
                          "taerra.settings.layer_permission",
                          "taerra.layer." + layer.id().replace(':', '.'))
                      : Component.translatable("taerra.settings.layer_gone"),
                  rowWidth() - 116);
      this.toggle =
          CycleButton.booleanBuilder(
                  Component.translatable("taerra.settings.private").withStyle(ChatFormatting.GOLD),
                  Component.translatable("taerra.settings.public"),
                  on(ServerSettingsScreen.this.draft.get(this.field)))
              .displayOnlyValue()
              .create(
                  0,
                  0,
                  60,
                  20,
                  this.label,
                  (_, on) -> {
                    set(this.field, new JsonPrimitive(on));
                    refill();
                  });
    }

    @Override
    int height() {
      return top() + 26;
    }

    private int top() {
      return Math.max(30, 22 + this.hint.size() * 10);
    }

    @Override
    @NotNull
    List<String> fields() {
      return List.of(this.field, this.iconField, this.scaleField);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
      return List.of(this.icon, this.toggle, this.scale);
    }

    @Override
    public @NotNull List<? extends NarratableEntry> narratables() {
      return List.of(this.icon, this.toggle, this.scale);
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      slab(graphics, 0, hovered, mouseX, mouseY);
      final @NotNull Font font = Minecraft.getInstance().font;
      graphics.text(font, this.label, getContentX() + 4, getY() + 6, 0xFFFFFFFF, true);
      for (int i = 0; i < this.hint.size(); i++) {
        graphics.text(
            font, this.hint.get(i), getContentX() + 4, getY() + 17 + i * 10, MUTED, false);
      }
      this.toggle.setPosition(
          getContentRight() - 6 - this.toggle.getWidth(),
          getY() + top() / 2 - this.toggle.getHeight() / 2);
      this.toggle.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.icon.setPosition(
          this.toggle.getX() - 6 - this.icon.getWidth(),
          getY() + top() / 2 - this.icon.getHeight() / 2);
      this.icon.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.scale.setPosition(getContentX() + 4, getY() + top());
      this.scale.setWidth(getContentRight() - 6 - getContentX() - 4);
      this.scale.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  private final class ScaleSlider extends AbstractSliderButton {

    private final @NotNull String field;
    private final @NotNull List<@Nullable Integer> stops;

    private ScaleSlider(final @NotNull String field) {
      super(0, 0, 0, 20, Component.empty(), 0);
      this.field = field;
      this.stops = stops(parseScale(string(ServerSettingsScreen.this.draft.get(field))));
      this.value =
          Math.max(
                  0,
                  this.stops.indexOf(
                      parseScale(string(ServerSettingsScreen.this.draft.get(field)))))
              / (double) (this.stops.size() - 1);
      updateMessage();
    }

    private @NotNull List<@Nullable Integer> stops(final @Nullable Integer current) {
      final @Nullable MapConfig config = ServerSettingsScreen.this.map.config();
      final int @NotNull [] range =
          config == null
              ? new int[] {-4, 2}
              : Projection.scaleRange(config, ServerSettingsScreen.this.map.worlds());
      final int from =
          Math.min(Math.min(range[0] + 1, range[1]), current == null ? range[1] : current);
      final int to = Math.max(range[1], current == null ? range[1] : current);
      final @NotNull List<@Nullable Integer> stops = new ArrayList<>();
      stops.add(null);
      for (int scale = from; scale <= to; scale++) {
        stops.add(scale);
      }
      return stops;
    }

    private int index() {
      return (int) Math.round(this.value * (this.stops.size() - 1));
    }

    @Override
    protected void updateMessage() {
      setMessage(
          Component.translatable("taerra.settings.layer_scale")
              .append(": ")
              .append(describeScale(this.stops.get(index()))));
    }

    @Override
    protected void applyValue() {
      this.value = index() / (double) (this.stops.size() - 1);
      final @NotNull String text = scaleText(this.stops.get(index()));
      if (!text.equals(string(ServerSettingsScreen.this.draft.get(this.field)))) {
        set(this.field, new JsonPrimitive(text));
      }
    }

    @Override
    public boolean keyPressed(final @NotNull KeyEvent event) {
      if (event.isLeft() || event.isRight()) {
        setValue(
            Math.clamp(index() + (event.isLeft() ? -1 : 1), 0, this.stops.size() - 1)
                / (double) (this.stops.size() - 1));
        return true;
      }
      return super.keyPressed(event);
    }
  }
}
