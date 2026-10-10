package net.aerulion.taerra.rendering.block;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockTypes {

  private static final @NotNull Object2IntOpenHashMap<String> IDS_BY_KEY =
      new Object2IntOpenHashMap<>();
  private static final @NotNull Reference2IntOpenHashMap<BlockType> IDS =
      new Reference2IntOpenHashMap<>();
  private static final @NotNull Reference2IntOpenHashMap<Block> IDS_BY_HANDLE =
      new Reference2IntOpenHashMap<>();
  private static final BlockType @NotNull [] BY_ID;

  public static final int AIR;

  static {
    final @NotNull List<BlockType> blockTypes = new ArrayList<>();
    RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK).forEach(blockTypes::add);
    BY_ID = blockTypes.toArray(BlockType[]::new);
    for (int id = 0; id < BY_ID.length; id++) {
      final @NotNull BlockType blockType = BY_ID[id];
      final @NotNull String namespacedKey = blockType.key().asString();
      IDS_BY_KEY.put(namespacedKey, id);
      final int separatorIndex = namespacedKey.indexOf(':');
      if (separatorIndex >= 0 && separatorIndex < namespacedKey.length() - 1) {
        IDS_BY_KEY.put(namespacedKey.substring(separatorIndex + 1), id);
      }
      IDS.put(blockType, id);
      IDS_BY_HANDLE.put(handle(blockType), id);
    }
    AIR = IDS.getInt(BlockType.AIR);
    IDS.defaultReturnValue(AIR);
    IDS_BY_KEY.defaultReturnValue(-1);
    IDS_BY_HANDLE.defaultReturnValue(AIR);
  }

  private BlockTypes() {
    super();
  }

  public static @Nullable BlockType byKey(final @NotNull String key) {
    final int id = IDS_BY_KEY.getInt(key);
    return id < 0 ? null : BY_ID[id];
  }

  public static int count() {
    return BY_ID.length;
  }

  public static int id(final @NotNull BlockType blockType) {
    return IDS.getInt(blockType);
  }

  public static int id(final @NotNull Block handle) {
    return IDS_BY_HANDLE.getInt(handle);
  }

  public static int idByName(final @NotNull String name) {
    final int id = IDS_BY_KEY.getInt(name);
    return id < 0 ? AIR : id;
  }

  public static @NotNull BlockType byId(final int id) {
    return BY_ID[id];
  }

  public static @NotNull Block handle(final @NotNull BlockType blockType) {
    return CraftBlockType.bukkitToMinecraftNew(blockType);
  }
}
