package net.aerulion.taerra.mod.gui.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.aerulion.taerra.mod.web.model.Sky;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldList extends PanelList<WorldList.Row> {

  private static final int ROW = 14;
  private static final int SKY_WIDTH = 42;

  private final Function<String, @Nullable Sky> skies;
  private final Consumer<World> onPick;

  public WorldList(final Function<String, @Nullable Sky> skies, final Consumer<World> onPick) {
    super(0, ROW);
    this.skies = skies;
    this.onPick = onPick;
  }

  private static boolean matches(final @NotNull World world, final @NotNull String needle) {
    return needle.isEmpty()
        || world.displayName().toLowerCase(Locale.ROOT).contains(needle)
        || world.name().toLowerCase(Locale.ROOT).contains(needle);
  }

  public void refresh(
      final @NotNull List<World> worlds,
      final @NotNull String query,
      final @Nullable String current) {
    final @NotNull String needle = query.strip().toLowerCase(Locale.ROOT);
    final @NotNull Map<String, List<World>> groups = new LinkedHashMap<>();
    for (final @NotNull World world : worlds) {
      if (matches(world, needle)) {
        groups.computeIfAbsent(world.environment(), _ -> new ArrayList<>()).add(world);
      }
    }
    final @NotNull List<Row> rows = new ArrayList<>();
    groups.forEach(
        (environment, members) -> {
          rows.add(new Heading(members.getFirst().environmentName()));
          members.forEach(world -> rows.add(new Item(world)));
        });
    replaceEntries(rows);
    final @Nullable Row shown =
        rows.stream()
            .filter(
                row -> row instanceof final @NotNull Item item && item.world.name().equals(current))
            .findFirst()
            .orElse(null);
    setSelected(shown);
    if (shown != null) {
      centerScrollOn(shown);
    } else {
      setScrollAmount(0);
    }
  }

  public @Nullable World first() {
    return children().stream()
        .filter(row -> row instanceof Item)
        .map(row -> ((Item) row).world)
        .findFirst()
        .orElse(null);
  }

  public int preferredWidth(final @NotNull Font font) {
    return children().stream()
            .mapToInt(
                row ->
                    font.width(row.label())
                        + (row instanceof final @NotNull Item item
                                && this.skies.apply(item.world.name()) != null
                            ? SKY_WIDTH
                            : 0))
            .max()
            .orElse(0)
        + 28;
  }

  public int preferredHeight() {
    return contentHeight();
  }

  @Override
  protected void extractListSeparators(final GuiGraphicsExtractor graphics) {}

  @Override
  protected void extractListBackground(final GuiGraphicsExtractor graphics) {}

  public abstract static class Row extends ObjectSelectionList.Entry<Row> {

    abstract Component label();

    @Override
    public @NotNull Component getNarration() {
      return label();
    }
  }

  static final class Heading extends Row {

    private final @NotNull Component label;

    private Heading(final @NotNull Component environment) {
      this.label = environment.copy().withStyle(ChatFormatting.GRAY);
    }

    @Override
    @NotNull
    Component label() {
      return this.label;
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      graphics.text(
          Minecraft.getInstance().font,
          this.label,
          getContentX(),
          getContentYMiddle() - 4,
          0xFFFFFFFF,
          false);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
      return false;
    }
  }

  final class Item extends Row {

    private final @NotNull World world;
    private final @NotNull Component label;

    private Item(final @NotNull World world) {
      this.world = world;
      this.label = world.label();
    }

    @Override
    @NotNull
    Component label() {
      return this.label;
    }

    @Override
    public void extractContent(
        final @NotNull GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final boolean hovered,
        final float partialTick) {
      graphics.text(
          Minecraft.getInstance().font,
          this.label,
          getContentX() + 6,
          getContentYMiddle() - 4,
          hovered ? 0xFFFFFF55 : 0xFFFFFFFF,
          true);
      final @Nullable Sky sky = WorldList.this.skies.apply(this.world.name());
      if (sky != null) {
        final @NotNull Font font = Minecraft.getInstance().font;
        final int right = getContentRight() - 4;
        graphics.text(
            font,
            sky.clock(),
            right - font.width(sky.clock()),
            getContentYMiddle() - 4,
            CommonColors.LIGHT_GRAY,
            true);
        SkyButton.drawGlyph(
            graphics,
            right - font.width(sky.clock()) - SkyButton.GLYPH - 4,
            getContentYMiddle() - 5,
            sky);
        if (hovered) {
          graphics.setTooltipForNextFrame(font, sky.describe(), mouseX, mouseY);
        }
      }
      if (hovered && this.world.restricted()) {
        graphics.setTooltipForNextFrame(
            Minecraft.getInstance().font, Component.translatable("taerra.private"), mouseX, mouseY);
      }
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
      AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
      WorldList.this.onPick.accept(this.world);
      return true;
    }
  }
}
