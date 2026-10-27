package net.aerulion.taerra.mod.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.util.PlayerInfos;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.WebMapListener;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.Marker;
import net.aerulion.taerra.mod.web.model.Player;
import net.aerulion.taerra.mod.web.model.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SearchScreen extends Screen {

  private static final int WIDTH = 300;
  private static final int TOP = 32;
  private static final int ROW = 24;
  private static final int LIMIT = 30;
  private static final Pattern COORDINATES =
      Pattern.compile(
          "(-?\\d+(?:\\.\\d+)?)(?:[\\s,]+(-?\\d+(?:\\.\\d+)?))?[\\s,]+(-?\\d+(?:\\.\\d+)?)");
  private static final Pattern WORD = Pattern.compile("[\\s_.,:'&()#!?+-]+");

  private final Screen parent;
  private final @NotNull WebMap map;
  private final @Nullable String currentWorld;
  private final @NotNull Consumer<Target> onPick;
  private final WebMapListener listener =
      new WebMapListener() {
        @Override
        public void markersChanged(final @NotNull String world) {
          refresh(false);
        }

        @Override
        public void playersChanged() {
          refresh(false);
        }
      };
  private @Nullable EditBox query;
  private @Nullable Results results;

  public SearchScreen(
      final Screen parent,
      final @NotNull WebMap map,
      final @Nullable String currentWorld,
      final @NotNull Consumer<Target> onPick) {
    super(Component.translatable("taerra.search"));
    this.parent = parent;
    this.map = map;
    this.currentWorld = currentWorld;
    this.onPick = onPick;
    map.worlds().forEach(world -> map.markers(world.name()));
  }

  private static @NotNull String fold(final @NotNull String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  private static int score(final @NotNull String label, final @NotNull String query) {
    final @NotNull String text = fold(label);
    if (text.startsWith(query)) {
      return 0;
    }
    for (final @NotNull String word : WORD.split(text)) {
      if (word.startsWith(query)) {
        return 1;
      }
    }
    return text.contains(query) ? 2 : -1;
  }

  @Override
  protected void init() {
    final int x = (this.width - WIDTH) / 2;
    final @NotNull EditBox query =
        new EditBox(this.font, x, TOP, WIDTH, 20, Component.translatable("taerra.search"));
    query.setHint(Component.translatable("taerra.search.hint"));
    query.setMaxLength(64);
    query.setValue(this.query == null ? "" : this.query.getValue());
    query.setResponder(_ -> refresh(true));
    this.query = addRenderableWidget(query);
    this.results =
        addRenderableWidget(new Results(x, TOP + 24, Math.max(0, this.height - TOP - 24 - 16)));
    setInitialFocus(query);
    refresh(true);
  }

  @Override
  public void added() {
    super.added();
    this.map.addListener(this.listener);
  }

  @Override
  public void removed() {
    this.map.removeListener(this.listener);
    super.removed();
  }

  @Override
  public void onClose() {
    this.minecraft.setScreenAndShow(this.parent);
  }

  @Override
  public boolean keyPressed(final KeyEvent event) {
    final @Nullable Results results = this.results;
    if (results != null && !results.children().isEmpty()) {
      final int index = results.children().indexOf(results.getSelected());
      if (event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_UP) {
        final int size = results.children().size();
        results.select(
            Math.floorMod(index + (event.key() == InputConstants.KEY_DOWN ? 1 : -1), size));
        return true;
      }
      if (event.key() == InputConstants.KEY_RETURN
          || event.key() == InputConstants.KEY_NUMPADENTER) {
        pick(results.children().get(Math.max(index, 0)).result);
        return true;
      }
    }
    return super.keyPressed(event);
  }

  private void pick(final @NotNull Result result) {
    onClose();
    this.onPick.accept(result.target());
  }

  private @Nullable Component elsewhere(final @NotNull String world) {
    return world.equals(this.currentWorld) ? null : worldName(world);
  }

  private @NotNull Component worldName(final @NotNull String name) {
    final @Nullable World world = this.map.world(name);
    return Component.literal(world == null ? name : world.displayName());
  }

  private @NotNull List<Result> candidates() {
    final @NotNull List<Result> all = new ArrayList<>();
    for (final @NotNull Player player : this.map.players()) {
      if (player.anonymous() || player.name() == null || !player.located()) {
        continue;
      }
      all.add(
          new Result(
              player.name(),
              null,
              player.coordinates(),
              elsewhere(player.world()),
              player,
              0,
              new Target(player.world(), player.x(), player.y(), player.z(), player.uuid(), null)));
    }
    final @NotNull List<World> worlds = new ArrayList<>(this.map.worlds());
    worlds.sort(Comparator.comparing(world -> !world.name().equals(this.currentWorld)));
    for (final @NotNull World world : worlds) {
      for (final @NotNull Layer layer : this.map.markers(world.name())) {
        for (final @NotNull Marker marker : layer.markers()) {
          if (marker.label() != null) {
            all.add(marker(world, layer, marker));
          }
        }
      }
    }
    for (final @NotNull World world : this.map.worlds()) {
      all.add(
          new Result(
              world.displayName(),
              null,
              null,
              null,
              null,
              0xFF48494A,
              new Target(
                  world.name(),
                  world.spawn().x() + .5,
                  (double) world.spawn().y(),
                  world.spawn().z() + .5,
                  null,
                  null)));
    }
    return all;
  }

  private @NotNull Result marker(
      final @NotNull World world, final @NotNull Layer layer, final @NotNull Marker marker) {
    double x = 0;
    @Nullable Double y = null;
    double z = 0;
    if (marker.position() != null) {
      x = marker.position().get(0);
      y = marker.position().get(1);
      z = marker.position().get(2);
    } else if (marker.points() != null && !marker.points().isEmpty()) {
      final @NotNull List<List<Double>> points = marker.points();
      x =
          (points.stream().mapToDouble(point -> point.getFirst()).min().orElse(0)
                  + points.stream().mapToDouble(point -> point.getFirst()).max().orElse(0))
              / 2;
      z =
          (points.stream().mapToDouble(List::getLast).min().orElse(0)
                  + points.stream().mapToDouble(List::getLast).max().orElse(0))
              / 2;
    }
    return new Result(
        marker.label(),
        marker.description(),
        layer.displayName(),
        elsewhere(world.name()),
        null,
        Colors.argb(marker.color()),
        new Target(world.name(), x, y, z, null, layer.minScale()));
  }

  private void refresh(final boolean reset) {
    final @Nullable Results results = this.results;
    final @Nullable EditBox query = this.query;
    if (results == null || query == null) {
      return;
    }
    final @NotNull String typed = query.getValue().strip();
    final @NotNull String needle = fold(typed);
    final @NotNull List<Result> found = new ArrayList<>();
    final @NotNull Matcher place = COORDINATES.matcher(typed);
    if (place.matches() && this.currentWorld != null) {
      final double x = Math.floor(Double.parseDouble(place.group(1)));
      final @Nullable Double y =
          place.group(2) == null ? null : Math.floor(Double.parseDouble(place.group(2)));
      final double z = Math.floor(Double.parseDouble(place.group(3)));
      found.add(
          new Result(
              Component.translatable("taerra.search.go_to", typed).getString(),
              null,
              null,
              null,
              null,
              0xFF48494A,
              new Target(this.currentWorld, x + .5, y, z + .5, null, null)));
    }
    final @NotNull List<Result> candidates = candidates();
    if (needle.isEmpty()) {
      found.addAll(candidates.subList(0, Math.min(LIMIT, candidates.size())));
    } else {
      final @NotNull List<Ranked> ranked = new ArrayList<>();
      for (int i = 0; i < candidates.size(); i++) {
        final @NotNull Result result = candidates.get(i);
        int rank = score(result.label(), needle);
        if (rank < 0
            && result.description() != null
            && fold(result.description()).contains(needle)) {
          rank = 3;
        }
        if (rank >= 0) {
          ranked.add(new Ranked(result, rank, i));
        }
      }
      ranked.sort(Comparator.comparingInt(Ranked::rank).thenComparingInt(Ranked::order));
      ranked.stream().limit(LIMIT).forEach(entry -> found.add(entry.result()));
    }
    results.show(found, reset);
  }

  @Override
  public void extractRenderState(
      final @NotNull GuiGraphicsExtractor graphics,
      final int mouseX,
      final int mouseY,
      final float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    graphics.centeredText(this.font, this.title, this.width / 2, TOP - 14, 0xFFFFFFFF);
    final @Nullable Results results = this.results;
    if (results != null && results.children().isEmpty()) {
      graphics.centeredText(
          this.font,
          Component.translatable("taerra.search.empty"),
          this.width / 2,
          TOP + 34,
          0xFFA0A0A0);
    }
  }

  public record Target(
      String world,
      double x,
      @Nullable Double y,
      double z,
      @Nullable String player,
      @Nullable Integer minScale) {}

  private record Result(
      String label,
      @Nullable String description,
      @Nullable Component detail,
      @Nullable Component tag,
      @Nullable Player player,
      int color,
      Target target) {}

  private record Ranked(Result result, int rank, int order) {}

  private final class Results extends ObjectSelectionList<Results.Row> {

    private Results(final int x, final int y, final int height) {
      super(Minecraft.getInstance(), WIDTH, height, y, ROW);
      setX(x);
    }

    private void show(final @NotNull List<Result> found, final boolean reset) {
      final @Nullable Row selected = getSelected();
      final double scroll = scrollAmount();
      replaceEntries(found.stream().map(Row::new).toList());
      if (reset || selected == null) {
        select(0);
        setScrollAmount(0);
        return;
      }
      setScrollAmount(scroll);
      final @NotNull Object key = key(selected.result);
      setSelected(
          children().stream().filter(row -> key(row.result).equals(key)).findFirst().orElse(null));
    }

    private static @NotNull Object key(final @NotNull Result result) {
      return result.player() != null ? result.player().uuid() : result.target();
    }

    private void select(final int index) {
      if (index >= 0 && index < children().size()) {
        final @NotNull Row row = children().get(index);
        setSelected(row);
        scrollToEntry(row);
      }
    }

    @Override
    public int getRowWidth() {
      return WIDTH - 12;
    }

    @Override
    protected int scrollBarX() {
      return getRight() - 6;
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {

      private final @NotNull Result result;

      private Row(final @NotNull Result result) {
        this.result = result;
      }

      @Override
      public void extractContent(
          final @NotNull GuiGraphicsExtractor graphics,
          final int mouseX,
          final int mouseY,
          final boolean hovered,
          final float partialTick) {
        final @NotNull Font font = Minecraft.getInstance().font;
        final int x = getContentX() + 2;
        final int y = getContentYMiddle() - 8;
        final @Nullable Player player = this.result.player();
        if (player != null) {
          final @Nullable PlayerInfo info = PlayerInfos.find(player.uuid());
          PlayerFaceExtractor.extractRenderState(
              graphics,
              info != null ? info.getSkin() : DefaultPlayerSkin.getDefaultSkin(),
              x,
              y,
              16);
        } else {
          graphics.fill(x, y, x + 16, y + 16, 0xFF1E1E1F);
          graphics.fill(x + 2, y + 2, x + 14, y + 14, this.result.color());
        }
        int room = getContentWidth() - 22;
        final @Nullable Component tag = this.result.tag();
        if (tag != null) {
          final int tagWidth = font.width(tag) + 6;
          final int tagX = getContentRight() - 2 - tagWidth;
          graphics.fill(tagX, y + 2, tagX + tagWidth, y + 14, 0xFF1E1E1F);
          graphics.text(font, tag, tagX + 3, y + 4, 0xFFA0A0A0, false);
          room -= tagWidth + 6;
        }
        final int line = this.result.detail() == null ? y + 4 : y;
        graphics.text(
            font,
            font.substrByWidth(Component.literal(this.result.label()), room).getString(),
            x + 20,
            line,
            hovered ? 0xFFFFFF55 : 0xFFFFFFFF,
            true);
        if (this.result.detail() != null) {
          graphics.text(
              font,
              font.substrByWidth(this.result.detail(), room).getString(),
              x + 20,
              y + 9,
              0xFFA0A0A0,
              false);
        }
      }

      @Override
      public @NotNull Component getNarration() {
        return Component.literal(this.result.label());
      }

      @Override
      public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        pick(this.result);
        return true;
      }
    }
  }
}
