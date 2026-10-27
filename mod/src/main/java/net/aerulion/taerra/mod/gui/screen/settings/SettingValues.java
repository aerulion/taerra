package net.aerulion.taerra.mod.gui.screen.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingValues {

  public static final String ORDER = "order";
  public static final List<String> SHAPES = List.of("none", "circle", "square", "rectangle");
  public static final List<String> VIEWS = List.of("both", "top_down", "isometric");
  private static final Pattern SCALE = Pattern.compile("(\\d{1,5})(px|blocks?)");
  private static final int MAX_COORDINATE = 30_000_000;
  private static final int MIN_SCALE = -12;
  private static final int MAX_SCALE = 8;

  private SettingValues() {}

  public static @NotNull String settingField(final @NotNull String key) {
    return "setting:" + key;
  }

  public static @NotNull String worldField(final @NotNull String id, final @NotNull String part) {
    return "world:" + id + ":" + part;
  }

  public static @NotNull String worldOf(final @NotNull String field) {
    return field.substring("world:".length(), field.lastIndexOf(':'));
  }

  public static @NotNull String layerField(final @NotNull String id) {
    return "layer:" + id;
  }

  public static @NotNull String layerIconField(final @NotNull String id) {
    return "layer-icon:" + id;
  }

  public static @NotNull String layerScaleField(final @NotNull String id) {
    return "layer-scale:" + id;
  }

  public static boolean isLimit(final @NotNull String field) {
    return field.startsWith("world:") && field.endsWith(":limit");
  }

  public static boolean isStorage(final @NotNull String field) {
    return field.startsWith("setting:storage.");
  }

  public static boolean isViews(final @NotNull String field) {
    return field.startsWith("world:") && field.endsWith(":views");
  }

  public static boolean same(final @Nullable JsonElement a, final @Nullable JsonElement b) {
    return Objects.equals(a == null ? JsonNull.INSTANCE : a, b == null ? JsonNull.INSTANCE : b);
  }

  public static @NotNull List<String> ids(final @Nullable JsonElement order) {
    final @NotNull List<String> ids = new ArrayList<>();
    if (order != null && order.isJsonArray()) {
      order.getAsJsonArray().forEach(id -> ids.add(id.getAsString()));
    }
    return ids;
  }

  public static @NotNull JsonArray array(final @NotNull List<String> ids) {
    final @NotNull JsonArray array = new JsonArray();
    ids.forEach(array::add);
    return array;
  }

  public static boolean sameOrder(final @Nullable JsonElement a, final @Nullable JsonElement b) {
    final @NotNull List<String> first = ids(a);
    final @NotNull List<String> second = ids(b);
    return first.stream()
        .filter(second::contains)
        .toList()
        .equals(second.stream().filter(first::contains).toList());
  }

  public static boolean on(final @Nullable JsonElement value) {
    return value != null
        && value.isJsonPrimitive()
        && value.getAsJsonPrimitive().isBoolean()
        && value.getAsBoolean();
  }

  public static @NotNull String string(final @Nullable JsonElement value) {
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  public static boolean outOfRange(
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

  public static @Nullable Integer parseScale(final @NotNull String text) {
    final @NotNull Matcher matcher = SCALE.matcher(text);
    if (!matcher.matches() || Integer.bitCount(Integer.parseInt(matcher.group(1))) != 1) {
      return null;
    }
    final int exponent = Integer.numberOfTrailingZeros(Integer.parseInt(matcher.group(1)));
    final int scale = matcher.group(2).equals("px") ? exponent : -exponent;
    return scale >= MIN_SCALE && scale <= MAX_SCALE ? scale : null;
  }

  public static @NotNull String scaleText(final @Nullable Integer scale) {
    if (scale == null) {
      return "";
    }
    return scale >= 0 ? (1 << scale) + "px" : (1 << -scale) + "blocks";
  }

  public static @NotNull String viewsOf(final @Nullable JsonElement value) {
    final @NotNull String views = string(value);
    return VIEWS.contains(views) ? views : "both";
  }

  public static @NotNull List<String> partsOf(final @NotNull String views) {
    return views.equals("both") ? List.of("top_down", "isometric") : List.of(views);
  }

  public static boolean addsView(
      final @Nullable JsonElement before, final @Nullable JsonElement after) {
    return !partsOf(viewsOf(before)).containsAll(partsOf(viewsOf(after)));
  }

  public static @NotNull String shapeOf(final @Nullable JsonElement value) {
    return value != null
            && value.isJsonObject()
            && value.getAsJsonObject().get("shape") instanceof final JsonPrimitive shape
            && SHAPES.contains(shape.getAsString())
        ? shape.getAsString()
        : "none";
  }

  public static @Nullable Integer bound(
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

  public static @NotNull JsonObject limit(
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

  public static boolean badLimit(final @Nullable JsonElement value) {
    final @NotNull String shape = shapeOf(value);
    if (shape.equals("none")) {
      return value != null
          && value.isJsonObject()
          && value.getAsJsonObject().get("shape") instanceof final JsonPrimitive given
          && !given.getAsString().equals("none");
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
    final int depth = maxZ - minZ;
    if (width < 0 || depth < 0) {
      return true;
    }
    return !shape.equals("rectangle") && (width != depth || width < 2 || width % 2 != 0);
  }

  public static @NotNull JsonObject reshape(
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
}
