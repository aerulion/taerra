package net.aerulion.taerra.rendering.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TileHierarchyTest {

  @Test
  @DisplayName("childrenOf and childIndex agree on slot ordering")
  void childIndexMatchesChildrenOfOrdering() {
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        final @NotNull TileCoordinate parent = TileCoordinate.topDown("w", 1, x, z);
        final TileCoordinate @NotNull [] children = TileHierarchy.childrenOf(parent);
        for (int index = 0; index < children.length; index++) {
          assertEquals(
              index,
              TileHierarchy.childIndex(children[index]),
              "slot mismatch for child " + index + " of " + parent);
        }
      }
    }
  }

  @Test
  @DisplayName("a tile's view survives walking up and down the pyramid")
  void hierarchyPreservesTheView() {
    for (final net.aerulion.taerra.model.MapView view :
        net.aerulion.taerra.model.MapView.values()) {
      final @NotNull TileCoordinate parent =
          new TileCoordinate("w", view, MapRotation.R0, 2, -3, 5);
      for (final @NotNull TileCoordinate child : TileHierarchy.childrenOf(parent)) {
        assertEquals(view, child.view(), "children must stay in their parent's view");
        assertEquals(parent, TileHierarchy.parentOf(child));
      }
    }
  }

  @Test
  @DisplayName("every child resolves back to its parent, including at negative coordinates")
  void parentOfInvertsChildrenOf() {
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        final @NotNull TileCoordinate parent = TileCoordinate.topDown("w", 2, x, z);
        for (final @NotNull TileCoordinate child : TileHierarchy.childrenOf(parent)) {
          assertEquals(parent, TileHierarchy.parentOf(child));
        }
      }
    }
  }
}
