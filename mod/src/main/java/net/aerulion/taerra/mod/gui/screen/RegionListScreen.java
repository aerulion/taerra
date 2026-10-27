package net.aerulion.taerra.mod.gui.screen;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.aerulion.taerra.mod.gui.widget.IconButton;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.Account;
import net.aerulion.taerra.mod.web.model.Regions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionListScreen extends Screen {

  private static final int ROW = 26;
  private static final int ICON = 16;
  private static final int TOOLS_Y = 24;
  private static final String[] FORGET = {
    "........", "...##...", "..#####.", ".#######", "########", "########", ".######.", "........"
  };

  private final @NotNull MapScreen parent;
  private final @NotNull WebMap map;
  private final @NotNull String world;
  private @NotNull String query = "";
  private @Nullable String armed;
  private @Nullable String forgetting;
  private @Nullable RegionList list;

  public RegionListScreen(
      final @NotNull MapScreen parent, final @NotNull WebMap map, final @NotNull String world) {
    super(Component.translatable("taerra.regions"));
    this.parent = parent;
    this.map = map;
    this.world = world;
  }

  private static @NotNull String fold(final @NotNull String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  private static @NotNull Component name(final Regions.@NotNull Region region) {
    return region.name().isEmpty()
        ? Component.translatable("taerra.regions.unnamed")
        : Component.literal(region.name());
  }

  private int rowWidth() {
    return Math.min(this.width - 24, 460);
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  protected void init() {
    final int left = (this.width - rowWidth()) / 2;
    final int button = 100;
    final @NotNull EditBox filter =
        new EditBox(
            this.font,
            left,
            TOOLS_Y,
            rowWidth() - button - 4,
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
    addRenderableWidget(
        Button.builder(
                Component.translatable("taerra.regions.new"),
                _ -> {
                  onClose();
                  this.parent.editRegion(null);
                })
            .bounds(left + rowWidth() - button, TOOLS_Y, button, 20)
            .build());
    addRenderableWidget(
        Button.builder(CommonComponents.GUI_DONE, _ -> onClose())
            .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
            .build());
    final int top = TOOLS_Y + 26;
    this.list = addRenderableWidget(new RegionList(top, Math.max(ROW, this.height - 40 - top)));
    refresh();
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    if (this.list != null && this.list.children().isEmpty()) {
      graphics.centeredText(
          this.font,
          Component.translatable(
              this.query.isBlank() ? "taerra.regions.none" : "taerra.waypoints.no_match"),
          this.width / 2,
          TOOLS_Y + 40,
          CommonColors.LIGHT_GRAY);
    }
  }

  private void refresh() {
    final @Nullable RegionList list = this.list;
    if (list == null) {
      return;
    }
    final @NotNull String query = fold(this.query.strip());
    final @Nullable List<Regions.Region> regions = this.map.regions(this.world).regions();
    list.show(
        (regions == null ? List.<Regions.Region>of() : regions)
            .stream()
                .filter(region -> query.isEmpty() || fold(name(region).getString()).contains(query))
                .sorted(
                    Comparator.comparing((Regions.Region region) -> fold(name(region).getString()))
                        .thenComparing(Regions.Region::id))
                .toList());
  }

  private void showOnMap(final Regions.@NotNull Region region) {
    onClose();
    this.parent.jump(
        new SearchScreen.Target(
            this.world, region.label()[0], null, region.label()[1], null, null));
  }

  private void edit(final Regions.@NotNull Region region) {
    onClose();
    this.parent.jump(
        new SearchScreen.Target(
            this.world, region.label()[0], null, region.label()[1], null, null));
    this.parent.editRegion(region);
  }

  private void delete(final Regions.@NotNull Region region) {
    if (!region.id().equals(this.armed)) {
      this.armed = region.id();
      refresh();
      return;
    }
    this.armed = null;
    this.map.deleteRegion(region.id(), (_, _) -> Minecraft.getInstance().execute(this::refresh));
  }

  private void forget(final Regions.@NotNull Region region) {
    if (!region.id().equals(this.forgetting)) {
      this.forgetting = region.id();
      refresh();
      return;
    }
    this.forgetting = null;
    this.map.resetRegion(region.id(), _ -> Minecraft.getInstance().execute(this::refresh));
  }

  private final class RegionList extends ContainerObjectSelectionList<RegionList.Entry> {

    private RegionList(final int top, final int height) {
      super(Minecraft.getInstance(), RegionListScreen.this.width, height, top, ROW);
    }

    private void show(final @NotNull List<Regions.Region> regions) {
      replaceEntries(regions.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
      return rowWidth();
    }

    private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {

      private final @NotNull List<IconButton> buttons = new ArrayList<>();
      private final @NotNull Component name;
      private final @NotNull Component meta;
      private final int color;

      private Entry(final Regions.@NotNull Region region) {
        this.name = name(region);
        this.meta =
            RegionScreen.meta(
                region.fog() == null ? "hide" : region.fog(),
                region.size() == null ? 0 : region.size());
        this.color = Colors.argb(region.color() == null ? "#ffffff" : region.color());
        button(WaypointListScreen.ON_MAP, "taerra.waypoints.show_on_map", () -> showOnMap(region));
        button(WaypointListScreen.EDIT, "taerra.waypoint.edit_short", () -> edit(region));
        final @Nullable Account account = RegionListScreen.this.map.account();
        if (account != null
            && account.resetRegions()
            && !"none".equals(region.fog())
            && !Boolean.FALSE.equals(region.discoverable())) {
          button(
              FORGET,
              region.id().equals(RegionListScreen.this.forgetting)
                  ? "taerra.region.reset_confirm"
                  : "taerra.region.reset",
              () -> forget(region));
        }
        button(
            WaypointListScreen.DELETE,
            region.id().equals(RegionListScreen.this.armed)
                ? "taerra.waypoint.confirm_delete"
                : "taerra.waypoint.delete",
            () -> delete(region));
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
        final @NotNull Font font = RegionListScreen.this.font;
        final int middle = getContentYMiddle();
        int x = getContentX();
        graphics.fill(x - 1, middle - 5, x + 9, middle + 5, 0xFF000000);
        graphics.fill(x, middle - 4, x + 8, middle + 4, Colors.withAlpha(0x60, this.color));
        graphics.fill(x, middle - 4, x + 8, middle - 3, this.color);
        graphics.fill(x, middle + 3, x + 8, middle + 4, this.color);
        graphics.fill(x, middle - 4, x + 1, middle + 4, this.color);
        graphics.fill(x + 7, middle - 4, x + 8, middle + 4, this.color);
        x += 14;
        int right = getContentRight();
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
          final @NotNull IconButton button = this.buttons.get(i);
          right -= ICON;
          button.setPosition(right, middle - ICON / 2);
          button.extractRenderState(graphics, mouseX, mouseY, partialTick);
          right -= 2;
        }
        final int room = Math.max(0, right - x - 6);
        graphics.text(font, cut(font, this.name, room), x, middle - 9, 0xFFFFFFFF, true);
        graphics.text(font, cut(font, this.meta, room), x, middle + 1, 0xFF909090, false);
      }

      private @NotNull String cut(
          final @NotNull Font font, final @NotNull Component text, final int room) {
        final @NotNull String plain = text.getString();
        return font.width(plain) <= room
            ? plain
            : font.plainSubstrByWidth(plain, Math.max(0, room - font.width("…"))) + "…";
      }

      @Override
      public @NotNull List<? extends GuiEventListener> children() {
        return this.buttons;
      }

      @Override
      public @NotNull List<? extends NarratableEntry> narratables() {
        return this.buttons;
      }
    }
  }
}
