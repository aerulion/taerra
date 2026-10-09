package net.aerulion.taerra.mod.gui.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.aerulion.taerra.mod.TaerraClient;
import net.aerulion.taerra.mod.gui.widget.IconButton;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.util.Distances;
import net.aerulion.taerra.mod.util.FileDialogs;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.PersonalWaypoint;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaypointListScreen extends Screen {

  private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
  private static final String FORMAT = "taerra-waypoints";
  private static final int VERSION = 1;
  private static final int MAX = 100;
  private static final int ROW = 26;
  private static final int ICON = 16;
  private static final int TOOLS_Y = 24;
  private static final int FOOTER_ROW = 24;
  private static final long STATUS_MILLIS = 6000;
  private static final String ALL = "";

  private static final String[] SHOWN = {
    "........", "........", "..####..", ".#.##.#.", "#..##..#", ".#....#.", "..####..", "........"
  };
  private static final String[] HIDDEN = {
    "........", ".......#", "..###.#.", ".#.#.#..", "#...#..#", ".#.#..#.", "..#..##.", ".#......"
  };
  private static final String[] ON_MAP = {
    "...##...", "...##...", "........", "##.##.##", "##.##.##", "........", "...##...", "...##..."
  };
  private static final String[] NAVIGATE = {
    "...##...", "..#..#..", ".#....#.", "###..###", "..#..#..", "..#..#..", "..#..#..", "..####.."
  };
  private static final String[] TELEPORT = {
    "..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#..#.#", "#..##..#", ".#....#.", "..####.."
  };
  private static final String[] EDIT = {
    ".....##.", "....#..#", "...#..#.", "..#..#..", ".#..#...", ".#.#....", ".##.....", "#......."
  };
  private static final String[] DELETE = {
    "..####..", "########", ".#....#.", ".#.##.#.", ".#.##.#.", ".#.##.#.", ".#....#.", "..####.."
  };

  private final @NotNull MapScreen parent;
  private final @NotNull WebMap map;
  private final @NotNull Set<String> selected = new HashSet<>();
  private @NotNull List<PersonalWaypoint> waypoints = List.of();
  private boolean loaded;
  private @NotNull String query = "";
  private @NotNull String world = ALL;
  private boolean byDistance;
  private @Nullable String armed;
  private boolean armedSelection;
  private @Nullable Incoming incoming;
  private @Nullable Component status;
  private boolean statusFailed;
  private long statusUntil;
  private @Nullable WaypointList list;
  private int footerRows = 1;

  public WaypointListScreen(final @NotNull MapScreen parent, final @NotNull WebMap map) {
    super(Component.translatable("taerra.waypoints"));
    this.parent = parent;
    this.map = map;
  }

  private static @NotNull String key(final @NotNull String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static @NotNull String fold(final @NotNull String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  private int rowWidth() {
    return Math.min(this.width - 24, 460);
  }

  @Override
  protected void init() {
    final int left = (this.width - rowWidth()) / 2;
    final int cycle = 100;
    final @NotNull EditBox filter =
        new EditBox(
            this.font,
            left,
            TOOLS_Y,
            rowWidth() - 2 * (cycle + 4),
            20,
            Component.translatable("taerra.waypoints.filter"));
    filter.setHint(Component.translatable("taerra.waypoints.filter"));
    filter.setValue(this.query);
    filter.setResponder(
        value -> {
          this.query = value;
          refresh();
        });
    addRenderableWidget(filter);
    final @NotNull List<String> worlds =
        Stream.concat(Stream.of(ALL), this.map.worlds().stream().map(World::name)).toList();
    if (!worlds.contains(this.world)) {
      this.world = ALL;
    }
    addRenderableWidget(
        new CycleButton.Builder<String>(this::worldLabel, () -> this.world)
            .withValues(worlds)
            .displayOnlyValue()
            .create(
                left + rowWidth() - 2 * cycle - 4,
                TOOLS_Y,
                cycle,
                20,
                Component.translatable("taerra.waypoints.world"),
                (_, value) -> {
                  this.world = value;
                  refresh();
                }));
    addRenderableWidget(
        CycleButton.booleanBuilder(
                Component.translatable("taerra.waypoints.sort.distance"),
                Component.translatable("taerra.waypoints.sort.name"),
                this.byDistance)
            .displayOnlyValue()
            .create(
                left + rowWidth() - cycle,
                TOOLS_Y,
                cycle,
                20,
                Component.translatable("taerra.waypoints.sort"),
                (_, distance) -> {
                  this.byDistance = distance;
                  refresh();
                }));
    this.footerRows = addFooter(left);
    final int top = TOOLS_Y + 26;
    final int bottom = this.height - 52 - FOOTER_ROW * (this.footerRows - 1);
    this.list = addRenderableWidget(new WaypointList(top, Math.max(ROW, bottom - top)));
    if (this.loaded) {
      refresh();
    } else {
      load();
    }
  }

  private int addFooter(final int left) {
    final @NotNull List<Button> buttons = new ArrayList<>();
    if (this.incoming != null) {
      final @NotNull Incoming incoming = this.incoming;
      buttons.add(
          Button.builder(
                  CommonComponents.GUI_CANCEL,
                  _ -> {
                    this.incoming = null;
                    rebuildWidgets();
                  })
              .width(80)
              .build());
      if (!this.waypoints.isEmpty()) {
        buttons.add(
            Button.builder(
                    Component.translatable(
                        "taerra.waypoints.import.replace", this.waypoints.size()),
                    _ -> importFile(incoming, "replace"))
                .width(120)
                .build());
      }
      buttons.add(
          Button.builder(
                  Component.translatable("taerra.waypoints.import.add"),
                  _ -> importFile(incoming, "add"))
              .width(120)
              .build());
    } else {
      if (!this.selected.isEmpty()) {
        buttons.add(
            Button.builder(
                    this.armedSelection
                        ? Component.translatable("taerra.waypoint.confirm_delete")
                            .withStyle(ChatFormatting.RED)
                        : Component.translatable("taerra.waypoints.delete", this.selected.size()),
                    _ -> deleteSelected())
                .width(100)
                .build());
      }
      buttons.add(
          Button.builder(Component.translatable("taerra.waypoints.import"), _ -> pickFile())
              .tooltip(Tooltip.create(Component.translatable("taerra.waypoints.import.tooltip")))
              .width(80)
              .build());
      final @NotNull Button export =
          Button.builder(
                  this.selected.isEmpty()
                      ? Component.translatable("taerra.waypoints.export.all")
                      : Component.translatable(
                          "taerra.waypoints.export.some", this.selected.size()),
                  _ -> exportFile())
              .width(100)
              .build();
      export.active = !this.waypoints.isEmpty();
      buttons.add(export);
      buttons.add(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).width(80).build());
    }
    final @NotNull List<List<Button>> rows = new ArrayList<>();
    for (final @NotNull Button button : buttons) {
      final @Nullable List<Button> last = rows.isEmpty() ? null : rows.getLast();
      if (last == null
          || last.stream().mapToInt(Button::getWidth).sum() + 4 * last.size() + button.getWidth()
              > rowWidth()) {
        rows.add(new ArrayList<>(List.of(button)));
      } else {
        last.add(button);
      }
    }
    for (int i = 0; i < rows.size(); i++) {
      final @NotNull List<Button> row = rows.get(i);
      int x = left + rowWidth();
      final int y = this.height - 26 - FOOTER_ROW * (rows.size() - 1 - i);
      for (int j = row.size() - 1; j >= 0; j--) {
        final @NotNull Button button = row.get(j);
        x -= button.getWidth();
        button.setPosition(x, y);
        addRenderableWidget(button);
        x -= 4;
      }
    }
    return rows.size();
  }

  private @NotNull Component worldLabel(final @NotNull String name) {
    if (name.equals(ALL)) {
      return Component.translatable("taerra.waypoints.all_worlds");
    }
    final @Nullable World world = this.map.world(name);
    return Component.literal(world == null ? name : world.displayName());
  }

  private void load() {
    this.map.personalWaypoints(
        waypoints -> {
          this.loaded = true;
          this.waypoints = waypoints == null ? List.of() : waypoints;
          final @NotNull Set<String> names = new HashSet<>();
          this.waypoints.forEach(waypoint -> names.add(key(waypoint.name())));
          this.selected.retainAll(names);
          this.armed = null;
          this.armedSelection = false;
          rebuildWidgets();
        });
  }

  private double distance(final @NotNull PersonalWaypoint waypoint) {
    final @Nullable LocalPlayer self = this.minecraft.player;
    if (self == null || !waypoint.world().equals(Dimensions.current())) {
      return Double.NaN;
    }
    final double[] center = waypoint.center();
    return Math.hypot(center[0] - self.getX(), center[2] - self.getZ());
  }

  private @NotNull List<PersonalWaypoint> visible() {
    final @NotNull String needle = fold(this.query.strip());
    final @NotNull Comparator<PersonalWaypoint> byName =
        Comparator.comparing(waypoint -> fold(waypoint.name()));
    return this.waypoints.stream()
        .filter(waypoint -> this.world.equals(ALL) || waypoint.world().equals(this.world))
        .filter(
            waypoint ->
                needle.isEmpty()
                    || fold(waypoint.name()).contains(needle)
                    || (waypoint.description() != null
                        && fold(waypoint.description()).contains(needle)))
        .sorted(
            this.byDistance
                ? Comparator.<PersonalWaypoint>comparingDouble(
                        waypoint -> {
                          final double distance = distance(waypoint);
                          return Double.isNaN(distance) ? Double.MAX_VALUE : distance;
                        })
                    .thenComparing(byName)
                : byName)
        .toList();
  }

  private void refresh() {
    if (this.list != null) {
      this.list.show(visible());
    }
  }

  private void show(final @NotNull Component message, final boolean failed) {
    this.status = message;
    this.statusFailed = failed;
    this.statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
  }

  private void toggleSelected(final @NotNull PersonalWaypoint waypoint, final boolean on) {
    if (on) {
      this.selected.add(key(waypoint.name()));
    } else {
      this.selected.remove(key(waypoint.name()));
    }
    this.armedSelection = false;
    rebuildWidgets();
  }

  private void setHidden(final @NotNull PersonalWaypoint waypoint) {
    this.map.setHidden(
        List.of(waypoint.name()),
        !waypoint.hidden(),
        status -> {
          if (status == 200) {
            load();
          } else {
            show(Component.translatable("taerra.waypoints.failed"), true);
          }
        });
  }

  private void showOnMap(final @NotNull PersonalWaypoint waypoint) {
    final double[] center = waypoint.center();
    this.minecraft.setScreenAndShow(this.parent);
    this.parent.jump(
        new SearchScreen.Target(
            waypoint.world(),
            center[0],
            Double.isNaN(center[1]) ? null : center[1],
            center[2],
            null));
  }

  private void navigate(final @NotNull PersonalWaypoint waypoint) {
    final double[] center = waypoint.center();
    TaerraClient.navigation()
        .start(
            new Navigation.Target(
                waypoint.name(),
                waypoint.world(),
                center[0],
                center[1],
                center[2],
                Colors.argb(waypoint.color() == null ? "#ffffff" : waypoint.color()),
                null));
    this.minecraft.setScreenAndShow(null);
  }

  private void teleport(final @NotNull PersonalWaypoint waypoint) {
    if (waypoint.x() == null || waypoint.z() == null) {
      return;
    }
    this.map.teleport(
        waypoint.world(),
        waypoint.x(),
        waypoint.y(),
        waypoint.z(),
        status ->
            show(
                Component.translatable(
                    switch (status) {
                      case 204 -> "taerra.waypoints.teleported";
                      case 409 -> "taerra.menu.offline";
                      default -> "taerra.menu.failed";
                    }),
                status != 204));
  }

  private void edit(final @NotNull PersonalWaypoint waypoint) {
    this.minecraft.setScreenAndShow(this.parent);
    this.parent.editWaypoint(waypoint.editable());
  }

  private void delete(final @NotNull PersonalWaypoint waypoint) {
    if (!key(waypoint.name()).equals(this.armed)) {
      this.armed = key(waypoint.name());
      show(Component.translatable("taerra.waypoints.confirm", waypoint.name()), true);
      refresh();
      return;
    }
    remove(List.of(waypoint));
  }

  private void deleteSelected() {
    if (!this.armedSelection) {
      this.armedSelection = true;
      rebuildWidgets();
      return;
    }
    remove(
        this.waypoints.stream()
            .filter(waypoint -> this.selected.contains(key(waypoint.name())))
            .toList());
  }

  private void remove(final @NotNull List<PersonalWaypoint> doomed) {
    if (doomed.isEmpty()) {
      load();
      return;
    }
    final int[] pending = {doomed.size()};
    final boolean[] failed = {false};
    for (final @NotNull PersonalWaypoint waypoint : doomed) {
      this.map.deleteWaypoint(
          waypoint.world(),
          waypoint.name(),
          true,
          status -> {
            if (status == 204) {
              this.selected.remove(key(waypoint.name()));
            } else {
              failed[0] = true;
            }
            if (--pending[0] == 0) {
              if (failed[0]) {
                show(Component.translatable("taerra.waypoints.failed"), true);
              }
              load();
            }
          });
    }
  }

  private void exportFile() {
    final @NotNull List<PersonalWaypoint> chosen =
        this.selected.isEmpty()
            ? this.waypoints
            : this.waypoints.stream()
                .filter(waypoint -> this.selected.contains(key(waypoint.name())))
                .toList();
    final @NotNull JsonObject file = new JsonObject();
    file.addProperty("format", FORMAT);
    file.addProperty("version", VERSION);
    file.add("waypoints", PRETTY.toJsonTree(chosen));
    FileDialogs.save(
        Path.of(System.getProperty("user.home"), "taerra-waypoints-" + LocalDate.now() + ".json"),
        path -> {
          if (path == null) {
            return;
          }
          final @NotNull Path target =
              path.getFileName().toString().contains(".") ? path : Path.of(path + ".json");
          try {
            Files.writeString(target, PRETTY.toJson(file), StandardCharsets.UTF_8);
            show(
                Component.translatable(
                    "taerra.waypoints.exported", chosen.size(), target.getFileName().toString()),
                false);
          } catch (final IOException e) {
            show(Component.translatable("taerra.waypoints.failed"), true);
          }
        });
  }

  private void pickFile() {
    FileDialogs.open(
        path -> {
          if (path != null) {
            read(path);
          }
        });
  }

  @Override
  public void onFilesDrop(final @NotNull List<Path> paths) {
    paths.stream()
        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
        .findFirst()
        .ifPresent(this::read);
  }

  private void read(final @NotNull Path path) {
    final @NotNull String name = path.getFileName().toString();
    @Nullable JsonArray waypoints = null;
    try {
      final @NotNull JsonElement root =
          JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
      if (root.isJsonArray()) {
        waypoints = root.getAsJsonArray();
      } else if (root.isJsonObject()
          && root.getAsJsonObject().get("waypoints") instanceof final JsonArray array) {
        waypoints = array;
      }
    } catch (final IOException | RuntimeException e) {
      waypoints = null;
    }
    if (waypoints == null || waypoints.isEmpty()) {
      this.incoming = null;
      show(Component.translatable("taerra.waypoints.not_a_file", name), true);
    } else {
      this.incoming = new Incoming(name, waypoints);
      show(Component.translatable("taerra.waypoints.importing", name, waypoints.size()), false);
      this.statusUntil = Long.MAX_VALUE;
    }
    rebuildWidgets();
  }

  private void importFile(final @NotNull Incoming incoming, final @NotNull String mode) {
    this.incoming = null;
    rebuildWidgets();
    this.map.importWaypoints(
        mode,
        incoming.waypoints(),
        (status, body) -> {
          if (status != 200 || body == null) {
            show(
                status == 400
                    ? Component.translatable("taerra.waypoints.not_a_file", incoming.name())
                    : Component.translatable("taerra.waypoints.failed"),
                true);
            load();
            return;
          }
          final @NotNull JsonObject counts = JsonParser.parseString(body).getAsJsonObject();
          final int added = counts.get("added").getAsInt();
          final @NotNull List<Component> parts = new ArrayList<>();
          parts.add(
              added > 0
                  ? Component.translatable("taerra.waypoints.imported", added)
                  : Component.translatable("taerra.waypoints.imported_none"));
          final int taken = counts.get("taken").getAsInt();
          final int full = counts.get("full").getAsInt();
          final int invalid = counts.get("invalid").getAsInt();
          if (taken > 0) {
            parts.add(Component.translatable("taerra.waypoints.skipped.taken", taken));
          }
          if (full > 0) {
            parts.add(Component.translatable("taerra.waypoints.skipped.full", full, MAX));
          }
          if (invalid > 0) {
            parts.add(Component.translatable("taerra.waypoints.skipped.invalid", invalid));
          }
          final @NotNull MutableComponent message = Component.empty();
          for (int i = 0; i < parts.size(); i++) {
            message
                .append(i == 0 ? Component.empty() : Component.literal(" "))
                .append(parts.get(i));
          }
          show(message, added == 0 && parts.size() > 1);
          load();
        });
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    final @NotNull Component title =
        Component.empty()
            .append(this.title)
            .append(
                Component.literal("  " + this.waypoints.size() + " / " + MAX)
                    .withStyle(ChatFormatting.GRAY));
    graphics.centeredText(this.font, title, this.width / 2, 8, 0xFFFFFFFF);
    if (!this.loaded) {
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.waypoints.loading"),
          this.width / 2,
          this.height / 2 - 4,
          0xFFA0A0A0);
    } else if (this.list != null && this.list.children().isEmpty()) {
      graphics.centeredText(
          this.font,
          Component.translatable(
              this.waypoints.isEmpty() ? "taerra.waypoints.none" : "taerra.waypoints.no_match"),
          this.width / 2,
          this.height / 2 - 4,
          0xFFA0A0A0);
    }
    if (this.status != null && System.currentTimeMillis() < this.statusUntil) {
      graphics.centeredText(
          this.font,
          this.status,
          this.width / 2,
          this.height - 44 - FOOTER_ROW * (this.footerRows - 1),
          this.statusFailed ? 0xFFFF5555 : 0xFF55FF55);
    }
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  private record Incoming(@NotNull String name, @NotNull JsonArray waypoints) {}

  private final class WaypointList extends ContainerObjectSelectionList<WaypointList.Entry> {

    private WaypointList(final int top, final int height) {
      super(Minecraft.getInstance(), WaypointListScreen.this.width, height, top, ROW);
    }

    private void show(final @NotNull List<PersonalWaypoint> waypoints) {
      replaceEntries(waypoints.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
      return rowWidth();
    }

    private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {

      private final @NotNull PersonalWaypoint waypoint;
      private final @NotNull Checkbox check;
      private final @NotNull List<IconButton> buttons = new ArrayList<>();
      private final @NotNull Component name;
      private final @NotNull Component where;
      private final @NotNull String distance;
      private final int color;

      private Entry(final @NotNull PersonalWaypoint waypoint) {
        this.waypoint = waypoint;
        final @NotNull Font font = WaypointListScreen.this.font;
        this.check =
            Checkbox.builder(Component.empty(), font)
                .selected(WaypointListScreen.this.selected.contains(key(waypoint.name())))
                .onValueChange((_, on) -> toggleSelected(waypoint, on))
                .build();
        this.name = Component.literal(waypoint.name());
        final @NotNull String coordinates =
            waypoint.point()
                ? waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z()
                : Component.translatable("taerra.waypoints.type." + waypoint.type()).getString();
        this.where =
            Component.empty()
                .append(worldLabel(waypoint.world()))
                .append(" · " + coordinates)
                .append(
                    waypoint.description() == null || waypoint.description().isBlank()
                        ? ""
                        : " · " + waypoint.description().replace('\n', ' '));
        final double distance = distance(waypoint);
        this.distance = Double.isNaN(distance) ? "" : Distances.format(distance);
        this.color = Colors.argb(waypoint.color() == null ? "#ffffff" : waypoint.color());
        button(
            waypoint.hidden() ? HIDDEN : SHOWN,
            waypoint.hidden() ? "taerra.waypoints.hidden" : "taerra.waypoints.shown",
            () -> setHidden(waypoint));
        button(ON_MAP, "taerra.waypoints.show_on_map", () -> showOnMap(waypoint));
        final @Nullable Account account = WaypointListScreen.this.map.account();
        if (waypoint.point()) {
          button(NAVIGATE, "taerra.waypoint.navigate", () -> navigate(waypoint));
          if (account != null && account.teleport()) {
            button(TELEPORT, "taerra.waypoint.teleport", () -> teleport(waypoint));
          }
        }
        button(EDIT, "taerra.waypoint.edit_short", () -> edit(waypoint));
        button(
            DELETE,
            key(waypoint.name()).equals(WaypointListScreen.this.armed)
                ? "taerra.waypoint.confirm_delete"
                : "taerra.waypoint.delete",
            () -> delete(waypoint));
      }

      private void button(
          final String @NotNull [] icon, final @NotNull String label, final @NotNull Runnable run) {
        final @NotNull IconButton button =
            new IconButton(ICON, icon, Component.translatable(label), run);
        button.setTooltip(Tooltip.create(Component.translatable(label)));
        this.buttons.add(button);
      }

      @Override
      public void extractContent(
          final GuiGraphicsExtractor graphics,
          final int mouseX,
          final int mouseY,
          final boolean hovered,
          final float partialTick) {
        final @NotNull Font font = WaypointListScreen.this.font;
        final int middle = getContentYMiddle();
        int x = getContentX();
        this.check.setPosition(x, middle - this.check.getHeight() / 2);
        this.check.extractRenderState(graphics, mouseX, mouseY, partialTick);
        x += Checkbox.getBoxSize(font) + 6;
        drawIcon(graphics, x, middle - 4);
        x += 14;
        int right = getContentRight();
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
          final @NotNull IconButton button = this.buttons.get(i);
          right -= ICON;
          button.setPosition(right, middle - ICON / 2);
          button.extractRenderState(graphics, mouseX, mouseY, partialTick);
          right -= 2;
        }
        if (!this.distance.isEmpty()) {
          right -= font.width(this.distance) + 4;
          graphics.text(font, this.distance, right, middle - 4, 0xFFD0D1D4, true);
          right -= 6;
        }
        final int room = Math.max(0, right - x);
        final int text = this.waypoint.hidden() ? 0xFF808080 : 0xFFFFFFFF;
        graphics.text(font, cut(font, this.name, room), x, middle - 9, text, true);
        graphics.text(font, cut(font, this.where, room), x, middle + 1, 0xFF909090, false);
      }

      private @NotNull String cut(
          final @NotNull Font font, final @NotNull Component text, final int room) {
        final @NotNull String plain = text.getString();
        return font.width(plain) <= room
            ? plain
            : font.plainSubstrByWidth(plain, Math.max(0, room - font.width("…"))) + "…";
      }

      private void drawIcon(
          final @NotNull GuiGraphicsExtractor graphics, final int x, final int y) {
        final int color = this.waypoint.hidden() ? Colors.withAlpha(0x70, this.color) : this.color;
        graphics.fill(x - 1, y - 1, x + 9, y + 9, 0xFF000000);
        if ("area".equals(this.waypoint.type())) {
          graphics.fill(x, y, x + 8, y + 8, Colors.withAlpha(0x60, this.color));
          graphics.fill(x, y, x + 8, y + 1, color);
          graphics.fill(x, y + 7, x + 8, y + 8, color);
          graphics.fill(x, y, x + 1, y + 8, color);
          graphics.fill(x + 7, y, x + 8, y + 8, color);
        } else if ("line".equals(this.waypoint.type())) {
          for (int i = 0; i < 8; i++) {
            graphics.fill(x + i, y + 7 - i, x + i + 1, y + 8 - i, color);
          }
        } else {
          graphics.fill(x, y, x + 8, y + 8, color);
        }
      }

      @Override
      public @NotNull List<? extends GuiEventListener> children() {
        final @NotNull List<GuiEventListener> children = new ArrayList<>();
        children.add(this.check);
        children.addAll(this.buttons);
        return children;
      }

      @Override
      public @NotNull List<? extends NarratableEntry> narratables() {
        final @NotNull List<NarratableEntry> narratables = new ArrayList<>();
        narratables.add(this.check);
        narratables.addAll(this.buttons);
        return narratables;
      }
    }
  }
}
