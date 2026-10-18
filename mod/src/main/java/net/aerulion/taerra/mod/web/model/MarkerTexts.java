package net.aerulion.taerra.mod.web.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.NotNull;

public final class MarkerTexts {

  private static final List<String> CHUNK_LEVELS = List.of("entity_ticking", "ticking", "border");

  private MarkerTexts() {}

  public static @NotNull Layer localize(final @NotNull Layer layer) {
    return switch (layer.id()) {
      case "chunks" ->
          withMarkers(layer, layer.markers().stream().map(MarkerTexts::chunk).toList());
      case "worldborder" ->
          withMarkers(
              layer,
              layer.markers().stream()
                  .map(
                      marker ->
                          with(marker, I18n.get("taerra.marker.worldborder"), details(marker)))
                  .toList());
      case "worldguard" ->
          withMarkers(
              layer,
              layer.markers().stream()
                  .map(marker -> with(marker, marker.label(), details(marker)))
                  .toList());
      default -> layer;
    };
  }

  private static List<List<String>> details(final @NotNull Marker marker) {
    return marker.details() == null
        ? null
        : marker.details().stream()
            .map(
                row ->
                    row.size() < 2
                        ? row
                        : List.of(
                            translatedOr("taerra.marker.detail." + row.get(0), row.get(0)),
                            row.get(0).equals("shape")
                                ? translatedOr("taerra.marker.shape." + row.get(1), row.get(1))
                                : row.get(1)))
            .toList();
  }

  private static @NotNull String translatedOr(
      final @NotNull String key, final @NotNull String fallback) {
    return Language.getInstance().has(key) ? I18n.get(key) : fallback;
  }

  private static @NotNull Marker chunk(final @NotNull Marker marker) {
    final @NotNull Map<String, String> facts =
        marker.details() == null
            ? Map.of()
            : marker.details().stream()
                .filter(row -> row.size() >= 2)
                .collect(Collectors.toMap(row -> row.get(0), row -> row.get(1), (a, _) -> a));
    final String level = facts.get("level");
    if (!CHUNK_LEVELS.contains(level)) {
      return marker;
    }
    final @NotNull List<String> split =
        CHUNK_LEVELS.stream()
            .filter(facts::containsKey)
            .map(shown -> I18n.get("taerra.chunks.split." + shown, facts.get(shown)))
            .toList();
    final @NotNull List<String> players = new ArrayList<>();
    if (facts.containsKey("players")) {
      players.add(facts.get("players"));
    }
    if (facts.containsKey("anonymous")) {
      players.add(I18n.get("taerra.chunks.anonymous", facts.get("anonymous")));
    }
    final @NotNull List<List<String>> rows = new ArrayList<>();
    row(rows, "level", I18n.get("taerra.chunks.description." + level));
    row(
        rows,
        "chunks",
        facts.get("chunks") + (split.size() > 1 ? " (" + String.join(", ", split) + ")" : ""));
    row(rows, "size", I18n.get("taerra.chunks.size", facts.get("width"), facts.get("depth")));
    row(
        rows,
        "blocks",
        I18n.get(
            "taerra.chunks.blocks",
            facts.get("minX"),
            facts.get("maxX"),
            facts.get("minZ"),
            facts.get("maxZ")));
    row(
        rows,
        "players",
        players.isEmpty() ? I18n.get("taerra.chunks.nobody") : String.join(", ", players));
    if (facts.containsKey("forced")) {
      row(rows, "forced", count(facts.get("forced")));
    }
    if (facts.containsKey("tickets")) {
      row(rows, "tickets", facts.get("tickets"));
    }
    row(rows, "view", count(facts.get("view")));
    row(rows, "simulation", count(facts.get("simulation")));
    return with(
        marker, I18n.get("taerra.chunks.level." + level) + ", " + count(facts.get("chunks")), rows);
  }

  private static void row(
      final @NotNull List<List<String>> rows, final @NotNull String key, final String value) {
    rows.add(List.of(I18n.get("taerra.chunks.detail." + key), value));
  }

  private static @NotNull String count(final String count) {
    return I18n.get(
        "1".equals(count) ? "taerra.chunks.count.one" : "taerra.chunks.count.other", count);
  }

  private static @NotNull Layer withMarkers(
      final @NotNull Layer layer, final @NotNull List<Marker> markers) {
    return new Layer(
        layer.id(), layer.label(), layer.shown(), layer.restricted(), layer.personal(), markers);
  }

  private static @NotNull Marker with(
      final @NotNull Marker marker, final String label, final List<List<String>> details) {
    return new Marker(
        marker.id(),
        label,
        marker.color(),
        marker.description(),
        marker.type(),
        details,
        marker.position(),
        marker.points(),
        marker.minY(),
        marker.maxY(),
        marker.hollow(),
        marker.fullHeight(),
        marker.smooth(),
        marker.muted());
  }
}
