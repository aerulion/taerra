package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiveSettingsTest {

  @Test
  @DisplayName("every setting takes effect without a restart")
  void everySettingIsLive() {
    for (final Setting setting : ConfigEditor.SETTINGS) {
      assertNotNull(LiveSettings.reaction(setting.key()), setting.key());
    }
  }

  @Test
  @DisplayName("only the port and host restart the web server, other web settings refresh it")
  void webSettings() {
    assertEquals(LiveSettings.Reaction.RESTART_WEB, LiveSettings.reaction("web.port"));
    assertEquals(LiveSettings.Reaction.RESTART_WEB, LiveSettings.reaction("web.host"));
    assertEquals(LiveSettings.Reaction.WEB_CHANGED, LiveSettings.reaction("web.name"));
    assertEquals(LiveSettings.Reaction.WEB_CHANGED, LiveSettings.reaction("web.public-url"));
  }

  @Test
  @DisplayName("marker settings restart their layer, unknown keys do nothing")
  void markerLayers() {
    assertEquals(
        LiveSettings.Reaction.MARKER_LAYER, LiveSettings.reaction("markers.towny.enabled"));
    assertEquals(
        LiveSettings.Reaction.MARKER_LAYER,
        LiveSettings.reaction("markers.loaded-chunks.shown-by-default"));
    assertNull(LiveSettings.reaction("markers.unknown.enabled"));
    assertNull(LiveSettings.reaction("nope"));
  }
}
