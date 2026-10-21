package net.aerulion.taerra.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.rendering.pipeline.RenderStatusSnapshot;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.web.auth.ModHandshake;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebugReport {

  public static final @NotNull String TITLE = "TAERRA debug";

  private static final @NotNull List<String> INTEGRATIONS =
      List.of(
          "WorldEdit",
          "WorldGuard",
          "LuckPerms",
          "Chunky",
          "ChunkyBorder",
          "Towny",
          "GriefPrevention",
          "PlotSquared");

  private final @NotNull Plugin plugin;
  private final @NotNull WebSettings web;
  private final @NotNull TaerraConfig configuration;
  private final @NotNull TileGeneratorService generator;
  private final @NotNull ModHandshake handshake;

  public DebugReport(
      final @NotNull Plugin plugin,
      final @NotNull WebSettings web,
      final @NotNull TaerraConfig configuration,
      final @NotNull TileGeneratorService generator,
      final @NotNull ModHandshake handshake) {
    super();
    this.plugin = plugin;
    this.web = web;
    this.configuration = configuration;
    this.generator = generator;
    this.handshake = handshake;
  }

  public static boolean subpath(final @NotNull String publicUrl) {
    return publicUrl.matches("(?i)https?://[^/]+/.+");
  }

  public @NotNull String text() {
    final @NotNull Runtime runtime = Runtime.getRuntime();
    final @NotNull RenderStatusSnapshot render = this.generator.statusSnapshot();
    final @NotNull String publicUrl = this.web.publicUrl();
    final @NotNull List<String> lines = new ArrayList<>();
    lines.add(TITLE);
    lines.add("Plugin: " + this.plugin.getPluginMeta().getVersion());
    lines.add("Server: " + Bukkit.getName() + " " + Bukkit.getVersion());
    lines.add(
        "Java: "
            + System.getProperty("java.version")
            + " ("
            + System.getProperty("java.vendor")
            + ")");
    lines.add(
        "OS: "
            + System.getProperty("os.name")
            + " "
            + System.getProperty("os.version")
            + " ("
            + System.getProperty("os.arch")
            + ")");
    lines.add(
        String.format(
            Locale.ROOT,
            "Memory: %d MB max, %d cores",
            runtime.maxMemory() / (1024 * 1024),
            runtime.availableProcessors()));
    lines.add(
        "Web: port "
            + this.web.port()
            + (publicUrl.isEmpty() ? ", no public URL" : ", public URL set")
            + (subpath(publicUrl) ? ", under a subpath" : ""));
    lines.add("Worlds: " + this.configuration.enabledWorlds().size() + " on the map");
    lines.add(
        String.format(
            Locale.ROOT,
            "Renderer: %s, %d base and %d zoom threads, %d tiles queued",
            render.running() ? "running" : "stopped",
            render.baseThreads(),
            render.zoomThreads(),
            render.totalQueuedTiles()));
    lines.add("Integrations: " + or(integrations()));
    lines.add("Mods: " + or(mods()));
    return String.join("\n", lines);
  }

  private static @NotNull String or(final @NotNull String list) {
    return list.isEmpty() ? "none" : list;
  }

  private static @NotNull String integrations() {
    return INTEGRATIONS.stream()
        .map(Bukkit.getPluginManager()::getPlugin)
        .filter(Objects::nonNull)
        .filter(Plugin::isEnabled)
        .map(found -> found.getName() + " " + found.getPluginMeta().getVersion())
        .collect(Collectors.joining(", "));
  }

  private @NotNull String mods() {
    return Bukkit.getOnlinePlayers().stream()
        .filter(ModHandshake::uses)
        .map(this::modVersion)
        .collect(Collectors.groupingBy(version -> version, Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
        .collect(Collectors.joining(", "));
  }

  private @NotNull String modVersion(final @NotNull Player player) {
    final @Nullable String version = this.handshake.version(player);
    return version == null ? "unknown" : version;
  }
}
