package net.aerulion.taerra.rendering.color;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Logger;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.util.HexColors;
import net.minecraft.world.level.block.RenderShape;
import org.bukkit.block.BlockType;
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

  private final BlockRenderData @NotNull [] entries = new BlockRenderData[BlockTypes.count()];

  private final BlockShape @NotNull [] shapes = new BlockShape[BlockTypes.count()];
  private int loadedEntries;

  public BlockDataStoreImpl(final @NotNull Logger logger) {
    super();
    this.logger = logger;
    load();
    resolveShapes();
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
      final @NotNull Map<String, BlockDataEntry> raw =
          OBJECT_MAPPER.readValue(
              inputStream,
              OBJECT_MAPPER
                  .getTypeFactory()
                  .constructMapType(Map.class, String.class, BlockDataEntry.class));
      int unknownBlocks = 0;
      for (final Map.@NotNull Entry<String, BlockDataEntry> entry : raw.entrySet()) {
        final @Nullable BlockType blockType =
            BlockTypes.byKey(entry.getKey().toLowerCase(Locale.ROOT));
        if (blockType == null) {
          unknownBlocks++;
          continue;
        }
        final @NotNull BlockDataEntry value = entry.getValue();
        this.entries[BlockTypes.id(blockType)] =
            new BlockRenderData(
                parseRgb(value.top()),
                parseRgb(value.side()),
                parseShape(value.shape()),
                Boolean.TRUE.equals(value.tintTop()),
                Boolean.TRUE.equals(value.tintSide()));
        this.loadedEntries++;
      }
      final int invisibleBlocks = markInvisibleBlocks();
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
    for (int id = 0; id < this.shapes.length; id++) {
      final @NotNull BlockType blockType = BlockTypes.byId(id);
      this.shapes[id] = blockType.isAir() ? BlockShape.NONE : dataFor(blockType).shape();
    }
  }

  private int markInvisibleBlocks() {
    int marked = 0;
    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @Nullable BlockRenderData filled =
          invisibleFallback(
              this.entries[BlockTypes.id(blockType)],
              BlockTypes.handle(blockType).defaultBlockState().getRenderShape()
                  == RenderShape.INVISIBLE);
      if (filled != null) {
        this.entries[BlockTypes.id(blockType)] = filled;
        marked++;
      }
    }
    return marked;
  }

  @Override
  public @NotNull BlockRenderData dataFor(final @NotNull BlockType blockType) {
    final @Nullable BlockRenderData data = this.entries[BlockTypes.id(blockType)];
    return data == null ? FALLBACK : data;
  }

  @Override
  public @NotNull BlockShape shapeOf(final @NotNull BlockType blockType) {
    return this.shapes[BlockTypes.id(blockType)];
  }

  @Override
  public @NotNull BlockShape shapeOf(final int blockId) {
    return this.shapes[blockId];
  }
}
