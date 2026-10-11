package net.aerulion.taerra.rendering.source;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.aerulion.taerra.model.RegionCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionFileChunkSourceProvider implements ChunkSourceProvider, AutoCloseable {

  private static final @NotNull Logger LOGGER =
      Logger.getLogger(RegionFileChunkSourceProvider.class.getName());
  private static final long NBT_QUOTA_BYTES = 64L << 20;
  private static final @NotNull Set<String> WARNED_WORLDS = ConcurrentHashMap.newKeySet();

  private static final @NotNull Set<String> UNFINISHED_STATUSES =
      Set.of(
          "minecraft:empty",
          "minecraft:structure_starts",
          "minecraft:structure_references",
          "minecraft:biomes",
          "minecraft:noise",
          "minecraft:surface",
          "minecraft:carvers");

  private final int currentDataVersion;
  private final @NotNull String worldId;
  private final @NotNull Path regionDirectory;
  private final int minHeight;
  private final int maxHeight;
  private final @NotNull Map<RegionCoordinate, RegionFileReader> openRegionFiles = new HashMap<>();
  private long chunkReadNanos;
  private int chunksRead;

  public RegionFileChunkSourceProvider(final @NotNull World world) {
    super();
    this.currentDataVersion = SharedConstants.getCurrentVersion().dataVersion().version();
    this.worldId = WorldKeys.id(world);
    this.regionDirectory = RegionDirectoryResolver.resolveRegionDirectory(world);
    this.minHeight = world.getMinHeight();
    this.maxHeight = world.getMaxHeight();
  }

  static boolean isFinished(final @NotNull String status) {
    return !UNFINISHED_STATUSES.contains(status);
  }

  @Override
  public @Nullable ColumnSource sourceForChunk(final int chunkX, final int chunkZ) {
    final long startedAt = System.nanoTime();
    try {
      return readChunk(chunkX, chunkZ);
    } finally {
      this.chunkReadNanos += System.nanoTime() - startedAt;
      this.chunksRead++;
    }
  }

  private @Nullable ParsedAnvilChunk readChunk(final int chunkX, final int chunkZ) {
    final @NotNull RegionCoordinate coordinate = RegionCoordinate.fromChunk(chunkX, chunkZ);
    @Nullable Exception failure = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final @Nullable RegionFileReader region = regionFor(coordinate);
        if (region == null) {
          return null;
        }
        try (final @Nullable DataInputStream input = region.chunkStream(chunkX, chunkZ)) {
          if (input == null) {
            return null;
          }

          final @NotNull CollectFields fields =
              new CollectFields(
                  new FieldSelector(ListTag.TYPE, "sections"),
                  new FieldSelector(IntTag.TYPE, "xPos"),
                  new FieldSelector(IntTag.TYPE, "zPos"),
                  new FieldSelector(StringTag.TYPE, "Status"),
                  new FieldSelector(IntTag.TYPE, "DataVersion"));
          NbtIo.parse(input, fields, NbtAccounter.create(NBT_QUOTA_BYTES));
          if (fields.getResult() instanceof final @NotNull CompoundTag tag
              && tag.getIntOr("xPos", chunkX) == chunkX
              && tag.getIntOr("zPos", chunkZ) == chunkZ) {
            final int dataVersion = tag.getIntOr("DataVersion", 0);
            final @Nullable ChunkFormat format =
                ChunkFormat.of(dataVersion, this.currentDataVersion);
            if (format == null) {
              warnUnsupported(dataVersion);
              return null;
            }
            return isFinished(tag.getStringOr("Status", ""))
                ? ParsedAnvilChunk.fromNbt(tag, format, this.minHeight, this.maxHeight)
                : null;
          }
        }
      } catch (final Exception e) {
        failure = e;
      }
      final @Nullable RegionFileReader stale = this.openRegionFiles.remove(coordinate);
      if (stale != null) {
        stale.close();
      }
    }
    throw new IllegalStateException(
        "Failed to read chunk " + chunkX + ", " + chunkZ + " of " + this.worldId, failure);
  }

  private void warnUnsupported(final int dataVersion) {
    if (!WARNED_WORLDS.add(this.worldId)) {
      return;
    }
    LOGGER.warning(
        dataVersion > this.currentDataVersion
            ? "World "
                + this.worldId
                + " has chunks from a newer Minecraft version (DataVersion "
                + dataVersion
                + ") than this server; they stay off the map"
            : "World "
                + this.worldId
                + " has chunks saved before Minecraft 26.2 (DataVersion "
                + dataVersion
                + ", oldest supported "
                + ChunkFormat.oldestDataVersion()
                + "); they stay off the map until the server is started with --forceUpgrade");
  }

  private @Nullable RegionFileReader regionFor(final @NotNull RegionCoordinate coordinate)
      throws IOException {
    if (this.openRegionFiles.containsKey(coordinate)) {
      return this.openRegionFiles.get(coordinate);
    }
    final @NotNull Path path =
        this.regionDirectory.resolve("r." + coordinate.x() + "." + coordinate.z() + ".mca");
    final @Nullable RegionFileReader reader =
        Files.isRegularFile(path) ? RegionFileReader.open(path) : null;
    this.openRegionFiles.put(coordinate, reader);
    return reader;
  }

  public long chunkReadNanos() {
    return this.chunkReadNanos;
  }

  public int chunksRead() {
    return this.chunksRead;
  }

  @Override
  public void close() {
    for (final @Nullable RegionFileReader reader : this.openRegionFiles.values()) {
      if (reader != null) {
        reader.close();
      }
    }
    this.openRegionFiles.clear();
  }
}
