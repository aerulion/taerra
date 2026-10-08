package net.aerulion.taerra.mod.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public final class TaerraKeys {

  private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath("taerra", "taerra"));

  public static final KeyMapping OPEN_MAP = key("key.taerra.map", InputConstants.KEY_M);
  public static final KeyMapping TOGGLE_MINIMAP = key("key.taerra.minimap", InputConstants.KEY_N);
  public static final KeyMapping MINIMAP_ZOOM_IN =
      key("key.taerra.minimap.zoom_in", InputConstants.KEY_EQUALS);
  public static final KeyMapping MINIMAP_ZOOM_OUT =
      key("key.taerra.minimap.zoom_out", InputConstants.KEY_MINUS);
  public static final KeyMapping TOGGLE_WAYPOINTS =
      key("key.taerra.waypoints", InputConstants.KEY_B);
  public static final KeyMapping TOP_DOWN = key("key.taerra.map.top_down", InputConstants.KEY_T);
  public static final KeyMapping ISOMETRIC = key("key.taerra.map.isometric", InputConstants.KEY_I);
  public static final KeyMapping ROTATE = key("key.taerra.map.rotate", InputConstants.KEY_R);
  public static final KeyMapping COLORS = key("key.taerra.map.colors", InputConstants.KEY_G);
  public static final KeyMapping PANELS = key("key.taerra.map.panels", InputConstants.KEY_P);
  public static final KeyMapping SEARCH = key("key.taerra.map.search", InputConstants.KEY_K);

  private TaerraKeys() {}

  private static @NotNull KeyMapping key(final @NotNull String name, final int key) {
    return new KeyMapping(name, InputConstants.Type.KEYBOARD, key, CATEGORY);
  }

  public static void register() {
    for (final @NotNull KeyMapping key :
        new KeyMapping[] {
          OPEN_MAP,
          TOGGLE_MINIMAP,
          MINIMAP_ZOOM_IN,
          MINIMAP_ZOOM_OUT,
          TOGGLE_WAYPOINTS,
          TOP_DOWN,
          ISOMETRIC,
          ROTATE,
          COLORS,
          PANELS,
          SEARCH
        }) {
      KeyMappingHelper.registerKeyMapping(key);
    }
  }
}
