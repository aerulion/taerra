package net.aerulion.taerra.rendering.source;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionDirectoryResolver {

  private static final @NotNull String REGION_FOLDER = "region";

  private RegionDirectoryResolver() {
    super();
  }

  public static @NotNull Path resolveRegionDirectory(final @NotNull World world) {
    return world.getWorldPath().resolve(REGION_FOLDER);
  }

  public static @Nullable Path resolveExistingRegionDirectory(final @NotNull World world) {
    final @NotNull Path regionDirectory = resolveRegionDirectory(world);
    return Files.isDirectory(regionDirectory) ? regionDirectory : null;
  }
}
