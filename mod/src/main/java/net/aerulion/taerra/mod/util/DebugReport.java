package net.aerulion.taerra.mod.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.aerulion.taerra.mod.TaerraClient;
import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebugReport {

  public static final @NotNull String MOD_BUG = "bug-mod.yml";

  private static final @NotNull Set<String> LISTED_APART =
      Set.of("taerra", "minecraft", "java", "fabricloader", "fabric-api");

  private DebugReport() {}

  public static @NotNull String text(final @Nullable WebMap map) {
    final @Nullable MapConfig config = map == null ? null : map.config();
    final @NotNull DeviceInfo gpu = RenderSystem.getDevice().getDeviceInfo();
    return String.join(
        "\n",
        List.of(
            "TAERRA debug",
            "Mod: " + TaerraClient.version() + ", protocol " + TaerraClient.PROTOCOL,
            "Minecraft: " + version("minecraft"),
            "Loader: Fabric Loader "
                + version("fabricloader")
                + ", Fabric API "
                + version("fabric-api"),
            "Java: "
                + System.getProperty("java.version")
                + " ("
                + System.getProperty("java.vendor")
                + ")",
            "OS: "
                + System.getProperty("os.name")
                + " "
                + System.getProperty("os.version")
                + " ("
                + System.getProperty("os.arch")
                + ")",
            "GPU: " + gpu.name() + " (" + gpu.backendName() + ", " + gpu.driverInfo() + ")",
            "Plugin: "
                + (config == null
                    ? "not connected"
                    : config.version() == null ? "older than reporting" : config.version()),
            "Mods: " + mods()));
  }

  private static @NotNull String version(final @NotNull String id) {
    return FabricLoader.getInstance()
        .getModContainer(id)
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
        .orElse("none");
  }

  private static @NotNull String mods() {
    final @NotNull String mods =
        FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> mod.getContainingMod().isEmpty())
            .filter(mod -> !"builtin".equals(mod.getMetadata().getType()))
            .filter(mod -> !LISTED_APART.contains(mod.getMetadata().getId()))
            .sorted(Comparator.comparing((ModContainer mod) -> mod.getMetadata().getId()))
            .map(
                mod ->
                    mod.getMetadata().getId()
                        + " "
                        + mod.getMetadata().getVersion().getFriendlyString())
            .collect(Collectors.joining(", "));
    return mods.isEmpty() ? "none" : mods;
  }
}
