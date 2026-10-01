package net.aerulion.taerra.rendering.color;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Colormaps {

  private static final @NotNull String RESOURCE_PATH = "/colormaps.json";
  private static final int SIZE = 256;
  private static final int TERMS = 6;

  private Colormaps() {
    super();
  }

  public static void load(final @NotNull Logger logger) {
    try {
      final @NotNull Map<String, Map<String, double[]>> colormaps = read();
      init(colormaps, "grass", GrassColor::init);
      init(colormaps, "foliage", FoliageColor::init);
      init(colormaps, "dry_foliage", DryFoliageColor::init);
    } catch (final IOException | RuntimeException e) {
      logger.log(Level.SEVERE, "Failed to load biome colormaps: " + e.getMessage(), e);
    }
  }

  static @NotNull Map<String, Map<String, double[]>> read() throws IOException {
    try (final @Nullable InputStream input = Colormaps.class.getResourceAsStream(RESOURCE_PATH)) {
      if (input == null) {
        throw new IOException("Colormap resource not found: " + RESOURCE_PATH);
      }
      return new ObjectMapper().readValue(input, new TypeReference<>() {});
    }
  }

  private static void init(
      final @NotNull Map<String, Map<String, double[]>> colormaps,
      final @NotNull String name,
      final @NotNull Consumer<int[]> init) {
    final @Nullable Map<String, double[]> coefficients = colormaps.get(name);
    if (coefficients == null) {
      throw new IllegalStateException(RESOURCE_PATH + " has no " + name + " colormap");
    }
    init.accept(pixels(coefficients));
  }

  static int @NotNull [] pixels(final @NotNull Map<String, double[]> coefficients) {
    final double @NotNull [] red = channel(coefficients, "red");
    final double @NotNull [] green = channel(coefficients, "green");
    final double @NotNull [] blue = channel(coefficients, "blue");
    final int @NotNull [] pixels = new int[SIZE * SIZE];
    for (int row = 0; row < SIZE; row++) {
      for (int column = 0; column < SIZE; column++) {
        final double x = column / (SIZE - 1.0);
        final double y = row / (SIZE - 1.0);
        pixels[row * SIZE + column] =
            0xFF000000
                | evaluate(red, x, y) << 16
                | evaluate(green, x, y) << 8
                | evaluate(blue, x, y);
      }
    }
    return pixels;
  }

  private static double @NotNull [] channel(
      final @NotNull Map<String, double[]> coefficients, final @NotNull String channel) {
    final double @Nullable [] values = coefficients.get(channel);
    if (values == null || values.length != TERMS) {
      throw new IllegalStateException(
          "A colormap needs " + TERMS + " " + channel + " coefficients");
    }
    return values;
  }

  private static int evaluate(final double @NotNull [] c, final double x, final double y) {
    final double value = c[0] + c[1] * y + c[2] * y * y + c[3] * x + c[4] * x * y + c[5] * x * x;
    return (int) Math.clamp(Math.round(value), 0, 255);
  }
}
