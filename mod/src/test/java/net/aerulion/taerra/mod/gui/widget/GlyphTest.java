package net.aerulion.taerra.mod.gui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.aerulion.taerra.mod.gui.screen.RegionListScreen;
import net.aerulion.taerra.mod.gui.screen.WaypointListScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GlyphTest {

  private static List<Glyph> glyphs() throws IllegalAccessException {
    final List<Glyph> glyphs = new ArrayList<>();
    for (final Class<?> type :
        List.of(
            IconButton.class,
            SkyButton.class,
            CompassButton.class,
            WaypointListScreen.class,
            RegionListScreen.class)) {
      for (final Field field : type.getDeclaredFields()) {
        if (field.getType() == Glyph.class && Modifier.isStatic(field.getModifiers())) {
          field.setAccessible(true);
          glyphs.add((Glyph) field.get(null));
        }
      }
    }
    return glyphs;
  }

  @Test
  @DisplayName("every glyph has a GUI sprite a resource pack can replace, at its drawn size")
  void spritesExist() throws Exception {
    final List<Glyph> glyphs = glyphs();
    assertEquals(20, glyphs.size());
    for (final Glyph glyph : glyphs) {
      final String path =
          "assets/%s/textures/gui/sprites/%s.png"
              .formatted(glyph.sprite().getNamespace(), glyph.sprite().getPath());
      try (final InputStream in = GlyphTest.class.getClassLoader().getResourceAsStream(path)) {
        assertNotNull(in, path);
        final BufferedImage image = ImageIO.read(in);
        assertEquals(glyph.width(), image.getWidth(), path);
        assertEquals(glyph.height(), image.getHeight(), path);
      }
    }
  }
}
