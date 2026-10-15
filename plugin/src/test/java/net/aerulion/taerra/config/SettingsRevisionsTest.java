package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.model.WorldLimit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsRevisionsTest {

  @Test
  @DisplayName("seeding marks nothing as changed, so a page opened before a restart saves cleanly")
  void seedChangesNothing() {
    final @NotNull SettingsRevisions revisions = new SettingsRevisions();
    revisions.seed(Map.of("setting:web.name", "A"), 1000);

    assertFalse(revisions.changedSince("setting:web.name", 0));
    assertEquals(1000, revisions.revision());
  }

  @Test
  @DisplayName("a change bumps only its own field, after the revision a page loaded")
  void trackBumpsChangedFields() {
    final @NotNull SettingsRevisions revisions = new SettingsRevisions();
    revisions.seed(Map.of("a", 1, "b", 1), 1000);
    final long loaded = revisions.revision();

    assertEquals(Set.of("a"), revisions.track(Map.of("a", 2, "b", 1), "aerulion", 1000));

    assertTrue(revisions.changedSince("a", loaded));
    assertFalse(revisions.changedSince("b", loaded));
    assertEquals("aerulion", revisions.author("a").name());
  }

  @Test
  @DisplayName("revisions keep rising even when the clock does not")
  void revisionsRise() {
    final @NotNull SettingsRevisions revisions = new SettingsRevisions();
    revisions.seed(Map.of("a", 1), 5000);
    revisions.track(Map.of("a", 2), null, 10);
    final long first = revisions.revision();
    revisions.track(Map.of("a", 3), null, 10);

    assertTrue(revisions.revision() > first);
    assertTrue(revisions.changedSince("a", first));
  }

  @Test
  @DisplayName("nothing changed tracks nothing")
  void unchangedTracksNothing() {
    final @NotNull SettingsRevisions revisions = new SettingsRevisions();
    revisions.seed(Map.of("a", 1), 1000);

    assertEquals(Set.of(), revisions.track(Map.of("a", 1), "aerulion", 2000));
    assertEquals(1000, revisions.revision());
  }

  @Test
  @DisplayName("a wanted order keeps worlds switched on meanwhile, at the end")
  void orderKeepsNewWorlds() {
    assertEquals(
        List.of("b", "a", "c"),
        ConfigEditor.order(List.of("b", "a", "gone"), List.of("a", "b", "c")));
  }

  @Test
  @DisplayName("limits are checked and written the way the settings page reads them")
  void limitsNormalize() {
    assertEquals(
        ConfigEditor.limitValue(WorldLimit.circle(0, 50, 100)),
        ConfigEditor.normalizeLimit(
            Map.of("shape", "circle", "minX", -100, "minZ", -50, "maxX", 100, "maxZ", 150)));
    assertEquals(
        ConfigEditor.limitValue(WorldLimit.rectangle(10, 20, -5, 7)),
        ConfigEditor.normalizeLimit(
            Map.of("shape", "rectangle", "minX", -5, "minZ", 7, "maxX", 10, "maxZ", 20)));
    assertEquals(Map.of("shape", "none"), ConfigEditor.normalizeLimit(Map.of("shape", "none")));
    assertNull(
        ConfigEditor.normalizeLimit(
            Map.of("shape", "square", "minX", 0, "minZ", 0, "maxX", 10, "maxZ", 12)));
    assertNull(
        ConfigEditor.normalizeLimit(
            Map.of("shape", "circle", "minX", 0, "minZ", 0, "maxX", 0, "maxZ", 0)));
    assertNull(
        ConfigEditor.normalizeLimit(
            Map.of("shape", "rectangle", "minX", 5, "minZ", 0, "maxX", 4, "maxZ", 1)));
    assertNull(
        ConfigEditor.normalizeLimit(
            Map.of("shape", "rectangle", "minX", 0.5, "minZ", 0, "maxX", 4, "maxZ", 1)));
    assertNull(
        ConfigEditor.normalizeLimit(
            Map.of("shape", "rectangle", "minX", 0, "minZ", 0, "maxX", 40_000_000, "maxZ", 1)));
    assertNull(ConfigEditor.normalizeLimit(Map.of("shape", "hexagon")));
  }
}
