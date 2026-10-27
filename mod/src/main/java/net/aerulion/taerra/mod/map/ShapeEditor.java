package net.aerulion.taerra.mod.map;

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
import java.util.function.Supplier;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.web.model.Regions;
import net.aerulion.taerra.mod.web.model.Waypoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShapeEditor {

  public static final List<Integer> BRUSH_SIZES = List.of(1, 3, 5);
  private static final int REGION_CHUNKS = 65536;
  private static final int HISTORY = 200;
  private static final List<String> REGION_PALETTE =
      List.of(
          "#3ab3da", "#b02e26", "#80c71f", "#f9801d", "#8932b8", "#fed83d", "#169c9c", "#c74ebd",
          "#3c44aa", "#5e7c16", "#f38baa", "#835432");

  private final @NotNull Supplier<@Nullable Regions> regions;
  private final @NotNull Map<Long, Regions.Region> owners = new HashMap<>();
  private final List<double[]> lasso = new ArrayList<>();
  private final List<double[]> path = new ArrayList<>();
  private final Deque<Edit> history = new ArrayDeque<>();
  private final Deque<Edit> future = new ArrayDeque<>();
  private @Nullable Regions indexed;
  private @Nullable Regions othersOf;
  private @Nullable String othersWithout;
  private @NotNull List<Drawing.Area> others = List.of();
  private @Nullable ChunkSelection selection;
  private @NotNull Kind kind = Kind.CHUNKS;
  private @NotNull Tool tool = Tool.BRUSH;
  private @NotNull Tool lastTool = Tool.BRUSH;
  private @Nullable List<double[]> pathBefore;
  private int vertex = -1;
  private boolean vertexExisting;
  private boolean vertexMoved;
  private boolean stroking;
  private boolean erasing;
  private @Nullable Place anchor;
  private @Nullable Place reach;
  private int brushSize = 1;
  private boolean eraseMode;
  private boolean smooth;
  private @NotNull String color = Waypoint.DEFAULT_COLOR;
  private @Nullable Waypoint editing;
  private @Nullable String editingId;
  private Regions.@Nullable Region region;
  private @Nullable String regionId;
  private @NotNull String regionName = "";
  private @NotNull String regionFog = "hide";
  private boolean regionDiscoverable = true;
  private @NotNull List<Long> hover = List.of();
  private boolean hoverErase;

  public ShapeEditor(final @NotNull Supplier<@Nullable Regions> regions) {
    this.regions = regions;
  }

  static @NotNull String freshColor(final @Nullable Regions regions) {
    final @NotNull Map<String, Integer> uses = new HashMap<>();
    for (final Regions.@NotNull Region region : regionsOf(regions)) {
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

  private static @NotNull List<Regions.Region> regionsOf(final @Nullable Regions regions) {
    return regions == null || regions.regions() == null ? List.of() : regions.regions();
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

  public void start(
      final @NotNull Kind kind,
      final @Nullable Waypoint editing,
      final @Nullable String editingId) {
    this.kind = kind;
    this.editing = editing;
    this.editingId = editingId;
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
    this.tool = editing != null || kind == Kind.LINE ? Tool.POINTS : this.lastTool;
  }

  public void startRegion(final Regions.@Nullable Region region) {
    this.region = region;
    this.regionId = region == null ? null : region.id();
    this.regionName = region == null ? "" : region.name();
    this.regionFog = region == null || region.fog() == null ? "hide" : region.fog();
    this.regionDiscoverable = region == null || !Boolean.FALSE.equals(region.discoverable());
    start(Kind.REGION, null, region == null ? null : "region:" + region.id());
    this.color =
        region != null && region.color() != null && Colors.isHex(region.color())
            ? region.color()
            : freshColor(this.regions.get());
    final @Nullable ChunkSelection selection = this.selection;
    if (region != null && region.chunks() != null && selection != null) {
      selection.addAll(
          region.chunks().stream().map(chunk -> ChunkSelection.key(chunk[0], chunk[1])).toList());
    }
  }

  public void regionSaved(
      final @NotNull String id,
      final @NotNull String name,
      final @NotNull String fog,
      final boolean discoverable,
      final @NotNull String color) {
    this.regionId = id;
    this.editingId = "region:" + id;
    this.regionName = name;
    this.regionFog = fog;
    this.regionDiscoverable = discoverable;
    this.color = color;
  }

  public void stop() {
    this.selection = null;
    this.stroking = false;
    this.path.clear();
    this.editing = null;
    this.editingId = null;
    this.vertex = -1;
  }

  public boolean active() {
    return this.selection != null;
  }

  public @Nullable ChunkSelection selection() {
    return this.selection;
  }

  public @NotNull Kind kind() {
    return this.kind;
  }

  public @NotNull Tool tool() {
    return this.tool;
  }

  public @NotNull List<Tool> tools() {
    return switch (this.kind) {
      case CHUNKS, REGION -> List.of(Tool.BRUSH, Tool.RECTANGLE, Tool.LASSO);
      case AREA -> List.of(Tool.values());
      case LINE -> List.of();
    };
  }

  public boolean offers(final @NotNull Tool tool) {
    return this.selection != null && tools().contains(tool) && tool != this.tool;
  }

  public boolean setTool(final @NotNull Tool tool) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null) {
      return false;
    }
    if (this.kind == Kind.AREA && (tool == Tool.POINTS) != (this.tool == Tool.POINTS)) {
      if (tool == Tool.POINTS && !selection.isEmpty()) {
        final @NotNull List<int[][]> rings = selection.rings();
        if (rings.size() != 1) {
          return false;
        }
        this.path.clear();
        this.path.addAll(ChunkSelection.corners(rings.getFirst()));
        selection.clear();
      } else if (tool != Tool.POINTS && this.path.size() >= 3) {
        selection.begin(false);
        selection.lasso(this.path.toArray(double[][]::new));
        selection.end();
        this.path.clear();
      }
      this.history.clear();
      this.future.clear();
    }
    this.tool = tool;
    if (tool != Tool.POINTS) {
      this.lastTool = tool;
    }
    return true;
  }

  public void pressPoint(
      final @NotNull Place place, final boolean remove, final int hit, final int segment) {
    this.pathBefore = copyPath();
    if (remove) {
      if (hit >= 0) {
        remember(new Edit(this.pathBefore, null));
        this.path.remove(hit);
      }
      return;
    }
    this.vertexExisting = hit >= 0;
    this.vertexMoved = false;
    this.vertex = hit >= 0 ? hit : segment < 0 ? this.path.size() : segment + 1;
    if (hit < 0) {
      this.path.add(this.vertex, new double[] {place.x() + .5, place.z() + .5});
    }
  }

  public boolean movingVertex() {
    return this.vertex >= 0;
  }

  public boolean moveVertex(final @Nullable Place place) {
    if (place == null || this.vertex < 0 || this.vertex >= this.path.size()) {
      return false;
    }
    final double @NotNull [] moved = {place.x() + .5, place.z() + .5};
    if (Arrays.equals(moved, this.path.get(this.vertex))) {
      return false;
    }
    this.path.set(this.vertex, moved);
    this.vertexMoved = true;
    return true;
  }

  public boolean releaseVertex() {
    if (this.vertex < 0) {
      return false;
    }
    if (this.vertexExisting && !this.vertexMoved && this.vertex < this.path.size()) {
      this.path.remove(this.vertex);
    }
    if (this.pathBefore != null && !samePath(this.pathBefore, this.path)) {
      remember(new Edit(this.pathBefore, null));
    }
    this.pathBefore = null;
    this.vertex = -1;
    return true;
  }

  public void beginStroke(final @NotNull Place place, final boolean shift) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null) {
      return;
    }
    this.stroking = true;
    this.erasing = this.eraseMode != shift;
    this.hover = List.of();
    selection.begin(this.erasing);
    this.anchor = place;
    this.reach = place;
    this.lasso.clear();
    this.lasso.add(new double[] {place.x() + .5, place.z() + .5});
    if (this.tool == Tool.BRUSH) {
      selection.brush(place.x(), place.z(), this.brushSize);
    }
  }

  public boolean stroking() {
    return this.stroking;
  }

  public void stroke(final @NotNull Place place) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null) {
      return;
    }
    this.reach = place;
    if (this.tool == Tool.BRUSH) {
      selection.brush(place.x(), place.z(), this.brushSize);
    } else if (this.tool == Tool.LASSO) {
      final double @NotNull [] last = this.lasso.getLast();
      if (Math.abs(last[0] - place.x() - .5) + Math.abs(last[1] - place.z() - .5) >= 1) {
        this.lasso.add(new double[] {place.x() + .5, place.z() + .5});
      }
    }
  }

  public boolean endStroke() {
    if (!this.stroking) {
      return false;
    }
    this.stroking = false;
    final @Nullable ChunkSelection selection = this.selection;
    if (selection != null && this.anchor != null && this.reach != null) {
      if (this.tool == Tool.RECTANGLE) {
        selection.box(this.anchor.x(), this.anchor.z(), this.reach.x(), this.reach.z());
      } else if (this.tool == Tool.LASSO) {
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
    return true;
  }

  public boolean removeLastPoint() {
    if (this.selection == null || this.tool != Tool.POINTS || this.path.isEmpty()) {
      return false;
    }
    remember(new Edit(copyPath(), null));
    this.path.removeLast();
    return true;
  }

  public boolean canUndo() {
    return !this.history.isEmpty();
  }

  public boolean canRedo() {
    return !this.future.isEmpty();
  }

  public boolean undo() {
    return step(this.history, this.future);
  }

  public boolean redo() {
    return step(this.future, this.history);
  }

  public int brushSize() {
    return this.brushSize;
  }

  public boolean setBrushSize(final int size) {
    if (this.selection == null || size == this.brushSize) {
      return false;
    }
    this.brushSize = size;
    return true;
  }

  public boolean stepBrushSize(final int by) {
    final int at = BRUSH_SIZES.indexOf(this.brushSize) + by;
    return setBrushSize(BRUSH_SIZES.get(Math.clamp(at, 0, BRUSH_SIZES.size() - 1)));
  }

  public boolean eraseMode() {
    return this.eraseMode;
  }

  public boolean setEraseMode(final boolean erase) {
    if (this.selection == null || erase == this.eraseMode) {
      return false;
    }
    this.eraseMode = erase;
    return true;
  }

  public boolean smooth() {
    return this.smooth;
  }

  public void setSmooth(final boolean smooth) {
    this.smooth = smooth;
  }

  public @NotNull String color() {
    return this.color;
  }

  public void setColor(final @NotNull String color) {
    this.color = color;
  }

  public @Nullable Waypoint editing() {
    return this.editing;
  }

  public Regions.@Nullable Region region() {
    return this.region;
  }

  public @Nullable String regionId() {
    return this.regionId;
  }

  public @NotNull String regionName() {
    return this.regionName;
  }

  public @NotNull String regionFog() {
    return this.regionFog;
  }

  public boolean regionDiscoverable() {
    return this.regionDiscoverable;
  }

  public int pointCount() {
    return this.path.size();
  }

  public boolean hover(final @Nullable Place place, final boolean shift) {
    @NotNull List<Long> hover = List.of();
    if (this.selection != null && !this.stroking && this.tool != Tool.POINTS && place != null) {
      hover =
          ChunkSelection.footprint(
              place.x(), place.z(), this.tool == Tool.BRUSH ? this.brushSize : 1);
    }
    final boolean erase = this.eraseMode != shift;
    if (hover.equals(this.hover) && erase == this.hoverErase) {
      return false;
    }
    this.hover = hover;
    this.hoverErase = erase;
    return true;
  }

  public boolean split() {
    final @Nullable ChunkSelection selection = this.selection;
    return selection != null
        && (this.kind == Kind.AREA && this.tool != Tool.POINTS && selection.rings().size() > 1
            || this.kind == Kind.REGION && !selection.connected());
  }

  public boolean full() {
    final @Nullable ChunkSelection selection = this.selection;
    return selection != null && this.tool != Tool.POINTS && selection.size() >= selection.limit();
  }

  public @Nullable List<double[]> shape() {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null) {
      return null;
    }
    if (this.kind == Kind.LINE) {
      return this.path.size() >= 2 ? this.path : null;
    }
    if (this.tool == Tool.POINTS) {
      return this.path.size() >= 3 ? this.path : null;
    }
    final @NotNull List<int[][]> rings = selection.rings();
    return rings.size() == 1 ? ChunkSelection.corners(rings.getFirst()) : null;
  }

  public boolean regionReady() {
    final @Nullable ChunkSelection selection = this.selection;
    return selection != null && !selection.isEmpty() && selection.connected();
  }

  public boolean ready() {
    return this.kind == Kind.REGION ? regionReady() : shape() != null;
  }

  public @Nullable List<List<Double>> points() {
    final @Nullable List<double[]> shape = shape();
    if (shape == null) {
      return null;
    }
    final @NotNull List<List<Double>> points = new ArrayList<>();
    for (final double @NotNull [] point : shape) {
      points.add(Arrays.asList(point[0], heightAt(point[0], point[1]), point[1]));
    }
    return points;
  }

  public @Nullable Drawing drawing() {
    final @Nullable ChunkSelection selection = this.selection;
    return selection == null
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
            this.kind == Kind.REGION ? others() : List.of());
  }

  public Regions.@Nullable Region owner(final int chunkX, final int chunkZ) {
    final @Nullable Regions regions = this.regions.get();
    if (regions == null) {
      return null;
    }
    if (regions != this.indexed) {
      this.indexed = regions;
      this.owners.clear();
      for (final Regions.@NotNull Region region : regionsOf(regions)) {
        for (final int @NotNull [] chunk :
            region.chunks() == null ? List.<int[]>of() : region.chunks()) {
          this.owners.put(ChunkSelection.key(chunk[0], chunk[1]), region);
        }
      }
    }
    return this.owners.get(ChunkSelection.key(chunkX, chunkZ));
  }

  private boolean taken(final long chunk) {
    final Regions.@Nullable Region owner =
        owner(ChunkSelection.chunkX(chunk), ChunkSelection.chunkZ(chunk));
    return owner != null && !owner.id().equals(this.regionId);
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

  private @NotNull List<double[]> copyPath() {
    return this.path.stream().map(double[]::clone).toList();
  }

  private void remember(final @NotNull Edit edit) {
    this.history.push(edit);
    while (this.history.size() > HISTORY) {
      this.history.removeLast();
    }
    this.future.clear();
  }

  private boolean step(final @NotNull Deque<Edit> from, final @NotNull Deque<Edit> to) {
    final @Nullable ChunkSelection selection = this.selection;
    if (selection == null || this.stroking || from.isEmpty()) {
      return false;
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
    return true;
  }

  private @NotNull List<Drawing.Area> others() {
    final @Nullable Regions regions = this.regions.get();
    if (regions == null) {
      return List.of();
    }
    if (regions != this.othersOf || !Objects.equals(this.othersWithout, this.regionId)) {
      this.othersOf = regions;
      this.othersWithout = this.regionId;
      final @NotNull List<Drawing.Area> areas = new ArrayList<>();
      for (final Regions.@NotNull Region other : regionsOf(regions)) {
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

  public enum Kind {
    CHUNKS,
    REGION,
    AREA,
    LINE
  }

  public enum Tool {
    BRUSH,
    RECTANGLE,
    LASSO,
    POINTS;

    public @NotNull String key() {
      return "taerra.selection.tool." + name().toLowerCase(Locale.ROOT);
    }
  }

  private record Edit(@Nullable List<double[]> points, ChunkSelection.@Nullable Change change) {}
}
