package net.aerulion.taerra.rendering.source;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

final class AnvilMaterials {

  private static final @NotNull Map<String, Material> BY_NAME = build();

  private AnvilMaterials() {
    super();
  }

  private static @NotNull Map<String, Material> build() {
    final @NotNull Map<String, Material> byName = new HashMap<>();
    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      byName.put(blockType.key().asString(), blockType.asMaterial());
    }
    return byName;
  }

  static @NotNull Material byName(final @NotNull String name) {
    return BY_NAME.getOrDefault(name, Material.AIR);
  }
}
