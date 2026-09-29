package net.aerulion.taerra.rendering.color;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Logger;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.util.HexColors;
import net.minecraft.world.level.block.RenderShape;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockDataStoreImpl implements BlockDataStore {

  private static final @NotNull String RESOURCE_PATH = "/block_data.json";

  private static final @NotNull ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final int OPAQUE = 0xFF000000;
  private static final int FALLBACK_RGB = 0xFFFF00FF;
  private static final @NotNull BlockRenderData FALLBACK =
      new BlockRenderData(FALLBACK_RGB, FALLBACK_RGB, BlockShape.FULL, false, false);

  private final @NotNull Logger logger;

  private final BlockRenderData @NotNull [] entries = new BlockRenderData[Material.values().length];

  private final BlockShape @NotNull [] shapes = new BlockShape[Material.values().length];
  private int loadedEntries;

  public BlockDataStoreImpl(final @NotNull Logger logger) {
    super();
    this.logger = logger;
    load();
  }

  private static int parseRgb(final @Nullable String value) {
    final @NotNull OptionalInt rgb = HexColors.parseRgb(value);
    return rgb.isPresent() ? OPAQUE | rgb.getAsInt() : FALLBACK_RGB;
  }

  private static @NotNull BlockShape parseShape(final @Nullable String value) {
    if (value == null) {
      return BlockShape.FULL;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "none" -> BlockShape.NONE;
      case "thin" -> BlockShape.THIN;
      default -> BlockShape.FULL;
    };
  }

  static @Nullable BlockRenderData invisibleFallback(
      final @Nullable BlockRenderData generated, final boolean renderedAsNothing) {
    if (generated != null || !renderedAsNothing) {
      return null;
    }
    return new BlockRenderData(FALLBACK_RGB, FALLBACK_RGB, BlockShape.NONE, false, false);
  }

  private void load() {
    try (final @Nullable InputStream inputStream =
            BlockDataStoreImpl.class.getResourceAsStream(RESOURCE_PATH)) {
      if (inputStream == null) {
        this.logger.severe("Missing block data resource " + RESOURCE_PATH);
        return;
      }
      final @NotNull Map<String, BlockType> validBlocksByKey = BlockTypes.byKey();
      final @NotNull Map<String, BlockDataEntry> raw =
          OBJECT_MAPPER.readValue(
              inputStream,
              OBJECT_MAPPER
                  .getTypeFactory()
                  .constructMapType(Map.class, String.class, BlockDataEntry.class));
      int unknownBlocks = 0;
      for (final Map.@NotNull Entry<String, BlockDataEntry> entry : raw.entrySet()) {
        final @Nullable BlockType blockType =
            validBlocksByKey.get(entry.getKey().toLowerCase(Locale.ROOT));
        if (blockType == null) {
          unknownBlocks++;
          continue;
        }
        final @NotNull BlockDataEntry value = entry.getValue();
        this.entries[blockType.asMaterial().ordinal()] =
            new BlockRenderData(
                parseRgb(value.top()),
                parseRgb(value.side()),
                parseShape(value.shape()),
                Boolean.TRUE.equals(value.tintTop()),
                Boolean.TRUE.equals(value.tintSide()));
        this.loadedEntries++;
      }
      final int invisibleBlocks = markInvisibleBlocks();
      resolveShapes();
      this.logger.fine(
          "Loaded "
              + this.loadedEntries
              + " block definitions ("
              + invisibleBlocks
              + " invisible, "
              + unknownBlocks
              + " unknown to this version)");
    } catch (final IOException e) {
      this.logger.severe(
          "Failed to read block data resource " + RESOURCE_PATH + ": " + e.getMessage());
    }
  }

  private void resolveShapes() {
    Arrays.fill(this.shapes, BlockShape.FULL);
    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @NotNull Material material = blockType.asMaterial();
      this.shapes[material.ordinal()] =
          material.isAir() ? BlockShape.NONE : dataFor(material).shape();
    }
  }

  private int markInvisibleBlocks() {
    int marked = 0;
    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @NotNull Material material = blockType.asMaterial();
      final @Nullable BlockRenderData filled =
          invisibleFallback(
              this.entries[material.ordinal()],
              CraftBlockType.bukkitToMinecraft(material).defaultBlockState().getRenderShape()
                  == RenderShape.INVISIBLE);
      if (filled != null) {
        this.entries[material.ordinal()] = filled;
        marked++;
      }
    }
    return marked;
  }

  @Override
  public @NotNull BlockRenderData dataFor(final @NotNull Material material) {
    final @Nullable BlockRenderData data = this.entries[material.ordinal()];
    return data == null ? FALLBACK : data;
  }

  @Override
  public @NotNull BlockShape shapeOf(final @NotNull Material material) {
    final @Nullable BlockShape shape = this.shapes[material.ordinal()];
    return shape == null ? (material.isAir() ? BlockShape.NONE : dataFor(material).shape()) : shape;
  }
}
