package net.aerulion.taerra.rendering.region;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aerulion.taerra.model.RegionCoordinate;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.source.RegionDirectoryResolver;
import net.aerulion.taerra.util.Throwables;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionFileScanner {

  private static final @NotNull Logger LOGGER = Logger.getLogger(RegionFileScanner.class.getName());
  private static final @NotNull Pattern REGION_FILE_PATTERN =
      Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
  private static final long MIN_REGION_HEADER_BYTES = 4096L;

  private RegionFileScanner() {
    super();
  }

  public static @NotNull RegionScanResult scanWorld(
      final @NotNull World world, final @NotNull RegionScanListener progressListener) {
    return scanWorld(world, _ -> true, _ -> true, progressListener);
  }

  public static @NotNull RegionScanResult scanWorld(
      final @NotNull World world,
      final @NotNull Predicate<? super RegionCoordinate> regionFilter,
      final @NotNull Predicate<? super TileCoordinate> tileFilter,
      final @NotNull RegionScanListener progressListener) {
    final @Nullable Path regionPath = RegionDirectoryResolver.resolveExistingRegionDirectory(world);
    if (regionPath == null) {
      LOGGER.warning(
          "Nothing to render in "
              + WorldKeys.id(world)
              + ", no region folder at "
              + RegionDirectoryResolver.resolveRegionDirectory(world));
      return new RegionScanResult(List.of(), 0, 0, 0);
    }

    final @NotNull List<RegionFileEntry> regionFiles = new ArrayList<>();
    final @Nullable File @Nullable [] files =
        regionPath.toFile().listFiles((dir, name) -> REGION_FILE_PATTERN.matcher(name).matches());
    if (files == null || files.length == 0) {
      LOGGER.info(
          "Nothing to render in " + WorldKeys.id(world) + ", no region files in " + regionPath);
      return new RegionScanResult(List.of(), 0, 0, 0);
    }

    for (final @Nullable File file : files) {
      if (file == null) {
        continue;
      }
      final @NotNull Matcher matcher = REGION_FILE_PATTERN.matcher(file.getName());
      if (!matcher.matches()) {
        continue;
      }
      final @NotNull RegionFileEntry regionFileEntry =
          new RegionFileEntry(
              file.toPath(),
              Integer.parseInt(matcher.group(1)),
              Integer.parseInt(matcher.group(2)));
      if (regionFilter.test(
          new RegionCoordinate(regionFileEntry.regionX(), regionFileEntry.regionZ()))) {
        regionFiles.add(regionFileEntry);
      }
    }

    regionFiles.sort(
        Comparator.comparingInt(RegionFileEntry::regionZ)
            .thenComparingInt(RegionFileEntry::regionX));

    final @NotNull Set<TileCoordinate> zoom0Tiles = new HashSet<>();
    int discoveredChunks = 0;
    int unreadableRegions = 0;

    for (int i = 0; i < regionFiles.size(); i++) {
      final RegionFileEntry region = regionFiles.get(i);
      try {
        discoveredChunks += scanRegion(region, WorldKeys.id(world), zoom0Tiles, tileFilter);
      } catch (final IOException | RuntimeException e) {
        unreadableRegions++;
        LOGGER.log(
            Level.WARNING,
            "Skipping unreadable region file "
                + region.path().getFileName()
                + " in "
                + WorldKeys.id(world)
                + ": "
                + Throwables.describe(e),
            e);
      }
      progressListener.onProgress(
          new RegionScanProgress(regionFiles.size(), i + 1, discoveredChunks, zoom0Tiles.size()));
    }

    final @NotNull List<TileCoordinate> orderedTiles = new ArrayList<>(zoom0Tiles);
    orderedTiles.sort(
        (left, right) -> {
          final int zCompare = Integer.compare(left.z(), right.z());
          if (zCompare != 0) {
            return zCompare;
          }
          final boolean forward = Math.floorMod(left.z(), 2) == 0;
          return forward
              ? Integer.compare(left.x(), right.x())
              : Integer.compare(right.x(), left.x());
        });

    final @NotNull Map<RegionCoordinate, List<TileCoordinate>> tilesByRegion =
        new LinkedHashMap<>();
    for (final @NotNull TileCoordinate tileCoordinate : orderedTiles) {
      tilesByRegion
          .computeIfAbsent(RegionCoordinate.fromZoom0Tile(tileCoordinate), key -> new ArrayList<>())
          .add(tileCoordinate);
    }

    final @NotNull List<TileCoordinate> regionGroupedTiles = new ArrayList<>(orderedTiles.size());
    tilesByRegion.entrySet().stream()
        .sorted(Comparator.comparingLong(entry -> entry.getKey().locality()))
        .forEach(entry -> regionGroupedTiles.addAll(entry.getValue()));

    return new RegionScanResult(
        regionGroupedTiles, discoveredChunks, regionFiles.size(), unreadableRegions);
  }

  private static int scanRegion(
      final @NotNull RegionFileEntry region,
      final String worldName,
      final @NotNull Set<? super TileCoordinate> zoom0Tiles,
      final @NotNull Predicate<? super TileCoordinate> tileFilter)
      throws IOException {
    int chunkCount = 0;
    final long fileLength = region.path().toFile().length();
    if (fileLength == 0) {
      return 0;
    }
    if (fileLength < MIN_REGION_HEADER_BYTES) {
      throw new IOException(
          "Region header truncated: expected at least "
              + MIN_REGION_HEADER_BYTES
              + " bytes but found "
              + fileLength
              + " bytes");
    }

    final byte @NotNull [] header = new byte[(int) MIN_REGION_HEADER_BYTES];
    try (final @NotNull RandomAccessFile file = new RandomAccessFile(region.path().toFile(), "r")) {
      file.readFully(header);
    }
    for (int index = 0; index < 1024; index++) {
      if (header[index * 4] == 0 && header[index * 4 + 1] == 0 && header[index * 4 + 2] == 0) {
        continue;
      }

      final int localChunkX = index % 32;
      final int localChunkZ = index / 32;
      final int globalChunkX = region.regionX() * 32 + localChunkX;
      final int globalChunkZ = region.regionZ() * 32 + localChunkZ;
      final @NotNull TileCoordinate tileCoordinate =
          TileGeometry.chunkToTile(globalChunkX, globalChunkZ, 0, worldName);
      if (tileFilter.test(tileCoordinate)) {
        zoom0Tiles.add(tileCoordinate);
      }
      chunkCount++;
    }

    return chunkCount;
  }
}
