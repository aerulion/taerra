package net.aerulion.taerra.waypoint;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.util.Names;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DynmapMarkers {

  private static final @NotNull String DEFAULT_SET = "markers";
  private static final @NotNull String DYNMAP_SPAWN = "_spawn_";
  private static final int DEFAULT_COLOR = 0x4FA3E0;
  private static final int @NotNull [] ICON_DYES = {
    0xB02E26, 0xF9801D, 0xFED83D, 0x80C71F, 0x5E7C16, 0x169C9C,
    0x3AB3DA, 0x3C44AA, 0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432
  };
  private static final @NotNull Map<String, Integer> ICON_COLORS =
      Map.of(
          "redflag", 0xB02E26,
          "orangeflag", 0xF9801D,
          "yellowflag", 0xFED83D,
          "greenflag", 0x5E7C16,
          "blueflag", 0x3C44AA,
          "purpleflag", 0x8932B8,
          "pinkflag", 0xF38BAA,
          "world", 0xFFCF3F);
  static final @NotNull Map<String, String> ICON_NAMES =
      Map.ofEntries(
          Map.entry("house", "home"),
          Map.entry("bighouse", "home"),
          Map.entry("anchor", "anchor"),
          Map.entry("bed", "bed"),
          Map.entry("chest", "chest"),
          Map.entry("church", "temple"),
          Map.entry("temple", "temple"),
          Map.entry("tower", "tower"),
          Map.entry("lighthouse", "tower"),
          Map.entry("tree", "tree"),
          Map.entry("redflag", "flag"),
          Map.entry("orangeflag", "flag"),
          Map.entry("yellowflag", "flag"),
          Map.entry("greenflag", "flag"),
          Map.entry("blueflag", "flag"),
          Map.entry("purpleflag", "flag"),
          Map.entry("pinkflag", "flag"),
          Map.entry("pirateflag", "flag"),
          Map.entry("star", "star"),
          Map.entry("goldstar", "star"),
          Map.entry("silverstar", "star"),
          Map.entry("bronzestar", "star"),
          Map.entry("world", "star"),
          Map.entry("heart", "heart"),
          Map.entry("skull", "skull"),
          Map.entry("king", "crown"),
          Map.entry("queen", "crown"),
          Map.entry("diamond", "gem"),
          Map.entry("ruby", "gem"),
          Map.entry("basket", "shop"),
          Map.entry("cart", "shop"),
          Map.entry("coins", "shop"),
          Map.entry("key", "key"),
          Map.entry("lock", "key"),
          Map.entry("fire", "fire"),
          Map.entry("exclamation", "warning"),
          Map.entry("warning", "warning"),
          Map.entry("caution", "warning"),
          Map.entry("sign", "info"),
          Map.entry("comment", "info"),
          Map.entry("walk", "person"),
          Map.entry("offlineuser", "person"),
          Map.entry("up", "arrow_up"),
          Map.entry("pointup", "arrow_up"),
          Map.entry("right", "arrow_right"),
          Map.entry("pointright", "arrow_right"),
          Map.entry("down", "arrow_down"),
          Map.entry("pointdown", "arrow_down"),
          Map.entry("left", "arrow_left"),
          Map.entry("pointleft", "arrow_left"));

  private DynmapMarkers() {
    super();
  }

  public static @NotNull Parsed parse(final @NotNull ConfigurationSection yaml) {
    final @NotNull List<Found> points = new ArrayList<>();
    final @NotNull Set<String> hidden = new HashSet<>();
    int shapes = 0;
    final @Nullable ConfigurationSection sets = yaml.getConfigurationSection("sets");
    if (sets == null) {
      return new Parsed(points, hidden, 0);
    }
    final @NotNull Map<String, Integer> seen = new HashMap<>();
    for (final @NotNull String setId : sets.getKeys(false)) {
      final @Nullable ConfigurationSection set = sets.getConfigurationSection(setId);
      if (set == null) {
        continue;
      }
      final @Nullable String group =
          setId.equals(DEFAULT_SET)
              ? null
              : truncate(text(set.getString("label"), setId), Names.MAX_LENGTH);
      if (group != null && set.getBoolean("hide")) {
        hidden.add(group);
      }
      final @Nullable ConfigurationSection markers = set.getConfigurationSection("markers");
      if (markers != null) {
        for (final @NotNull String id : markers.getKeys(false)) {
          final @Nullable ConfigurationSection marker = markers.getConfigurationSection(id);
          final @Nullable String world = marker == null ? null : marker.getString("world");
          if (world == null || id.startsWith(DYNMAP_SPAWN)) {
            continue;
          }
          final @NotNull String label =
              marker.getBoolean("markup")
                  ? stripMarkup(marker.getString("label", ""))
                  : marker.getString("label", "");
          points.add(
              new Found(
                  unique(text(label, id), seen),
                  world,
                  marker.getDouble("x"),
                  marker.getDouble("y"),
                  marker.getDouble("z"),
                  color(marker.getString("icon", "")),
                  icon(marker.getString("icon", "")),
                  group));
        }
      }
      shapes += count(set, "areas") + count(set, "lines") + count(set, "circles");
    }
    return new Parsed(points, hidden, shapes);
  }

  static int color(final @NotNull String icon) {
    final @NotNull String name = icon.toLowerCase(Locale.ROOT);
    if (name.isEmpty() || name.equals("default")) {
      return DEFAULT_COLOR;
    }
    final @Nullable Integer flag = ICON_COLORS.get(name);
    return flag != null ? flag : ICON_DYES[Math.floorMod(name.hashCode(), ICON_DYES.length)];
  }

  static @Nullable NamespacedKey icon(final @NotNull String icon) {
    final @Nullable String name = ICON_NAMES.get(icon.toLowerCase(Locale.ROOT));
    return name == null ? null : MarkerIcons.builtIn(name);
  }

  private static int count(final @NotNull ConfigurationSection set, final @NotNull String kind) {
    final @Nullable ConfigurationSection section = set.getConfigurationSection(kind);
    return section == null ? 0 : section.getKeys(false).size();
  }

  private static @NotNull String text(
      final @Nullable String value, final @NotNull String fallback) {
    final @NotNull String cleaned = clean(value);
    return !cleaned.isEmpty() ? cleaned : clean(fallback).isEmpty() ? "Waypoint" : clean(fallback);
  }

  static @NotNull String clean(final @Nullable String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value, Normalizer.Form.NFC)
            .replaceAll("[^" + Names.CHARACTERS + "]+", " ")
            .replaceAll(" {2,}", " ")
            .strip();
  }

  static @NotNull String stripMarkup(final @NotNull String html) {
    return html.replaceAll("<[^>]*>", " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replaceAll("\\s+", " ")
        .strip();
  }

  static @NotNull String unique(
      final @NotNull String label, final @NotNull Map<String, Integer> seen) {
    final @NotNull String name = truncate(label, Names.MAX_LENGTH);
    final int count = seen.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
    if (count == 1) {
      return name;
    }
    final @NotNull String suffix = " " + count;
    return truncate(name, Names.MAX_LENGTH - suffix.length()).strip() + suffix;
  }

  private static @NotNull String truncate(final @NotNull String value, final int length) {
    return value.length() <= length ? value : value.substring(0, length).strip();
  }

  public record Found(
      @NotNull String name,
      @NotNull String world,
      double x,
      double y,
      double z,
      int rgb,
      @Nullable NamespacedKey icon,
      @Nullable String group) {}

  public record Parsed(
      @NotNull List<Found> points, @NotNull Set<String> hiddenGroups, int shapes) {}
}
