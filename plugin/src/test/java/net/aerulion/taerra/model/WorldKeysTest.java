package net.aerulion.taerra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldKeysTest {

  @Test
  @DisplayName("a dimension key reads as words, whatever its namespace")
  void displayNameReadsAsWords() {
    assertEquals("Overworld", WorldKeys.displayName(NamespacedKey.minecraft("overworld")));
    assertEquals("The Nether", WorldKeys.displayName(NamespacedKey.minecraft("the_nether")));
    assertEquals(
        "Mining World", WorldKeys.displayName(new NamespacedKey("myplugin", "mining_world")));
    assertEquals("Maps Spawn", WorldKeys.displayName(new NamespacedKey("myplugin", "maps/spawn")));
  }
}
