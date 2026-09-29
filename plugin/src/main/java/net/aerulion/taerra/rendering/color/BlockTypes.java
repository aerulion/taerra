package net.aerulion.taerra.rendering.color;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

final class BlockTypes {

  private BlockTypes() {
    super();
  }

  static @NotNull Map<String, BlockType> byKey() {
    final @NotNull Map<String, BlockType> byKey = new Object2ObjectOpenHashMap<>();
    RegistryAccess.registryAccess()
        .getRegistry(RegistryKey.BLOCK)
        .forEach(
            blockType -> {
              final @NotNull String namespacedKey = blockType.key().asString();
              byKey.put(namespacedKey, blockType);
              final int separatorIndex = namespacedKey.indexOf(':');
              if (separatorIndex >= 0 && separatorIndex < namespacedKey.length() - 1) {
                byKey.put(namespacedKey.substring(separatorIndex + 1), blockType);
              }
            });
    return byKey;
  }
}
