package net.aerulion.taerra.web.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.web.auth.Viewer;
import net.aerulion.taerra.web.socket.MapWebSocketHandler;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SkyList {

  private static final int SUNSET = 12000;
  private static final int NIGHT = 13000;
  private static final int SUNRISE = 23000;
  private static final int DAY_TICKS = 24000;

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig taerraConfig;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull ObjectMapper mapper = new ObjectMapper();
  private volatile @NotNull Map<String, String> published = Map.of();

  public SkyList(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig taerraConfig,
      final @NotNull MapWebSocketHandler wsHandler) {
    super();
    this.plugin = plugin;
    this.taerraConfig = taerraConfig;
    this.wsHandler = wsHandler;
  }

  static @NotNull Map<String, Object> phase(final long ticks) {
    final int time = (int) ticks;
    final @NotNull String phase;
    final double progress;
    if (time >= SUNRISE) {
      phase = "sunrise";
      progress = (time - SUNRISE) / (double) (DAY_TICKS - SUNRISE);
    } else if (time >= NIGHT) {
      phase = "night";
      progress = (time - NIGHT) / (double) (SUNRISE - NIGHT);
    } else if (time >= SUNSET) {
      phase = "sunset";
      progress = (time - SUNSET) / (double) (NIGHT - SUNSET);
    } else {
      phase = "day";
      progress = time / (double) SUNSET;
    }
    final @NotNull Map<String, Object> sky = new LinkedHashMap<>();
    sky.put("phase", phase);
    sky.put("progress", Math.round(progress * 100) / 100.0);
    sky.put("time", time);
    return sky;
  }

  private static @Nullable Map<String, Object> sky(final @NotNull World world) {
    final @NotNull ServerLevel level = ((CraftWorld) world).getHandle();
    final @Nullable Holder<Timeline> day =
        level.dimensionType().timelines().stream()
            .filter(timeline -> timeline.is(Timelines.OVERWORLD_DAY))
            .findFirst()
            .orElse(null);
    if (day == null || day.value().periodTicks().orElse(0) != DAY_TICKS) {
      return null;
    }
    final @NotNull ClockInstance clock = level.clockManager().getInstance(day.value().clock());
    final @NotNull Map<String, Object> sky =
        phase(day.value().getCurrentTicks(level.clockManager()));
    sky.put("day", day.value().getPeriodCount(level.clockManager()));
    sky.put(
        "advancing",
        level.getGameRules().get(GameRules.ADVANCE_TIME) && !clock.isPaused() && clock.rate() > 0);
    sky.put("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
    return sky;
  }

  public @NotNull String visibleTo(final @NotNull Viewer viewer) {
    final @NotNull StringJoiner skies = new StringJoiner(",", "{", "}");
    this.published.forEach(
        (world, entry) -> {
          if (viewer.canSee(this.taerraConfig, world)) {
            skies.add(entry);
          }
        });
    return skies.toString();
  }

  public void publish() {
    final @NotNull Map<String, String> skies = new LinkedHashMap<>();
    try {
      for (final @NotNull World world : Bukkit.getWorlds()) {
        final @NotNull String name = WorldKeys.id(world);
        final @Nullable Map<String, Object> sky =
            this.taerraConfig.worldEnabled(name) ? sky(world) : null;
        if (sky != null) {
          skies.put(
              name,
              this.mapper.writeValueAsString(name) + ":" + this.mapper.writeValueAsString(sky));
        }
      }
    } catch (final JsonProcessingException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to serialize the sky", e);
      return;
    }
    if (skies.equals(this.published)) {
      return;
    }
    this.published = Map.copyOf(skies);
    this.wsHandler.broadcast(viewer -> "{\"type\":\"sky\",\"data\":" + visibleTo(viewer) + "}");
  }
}
