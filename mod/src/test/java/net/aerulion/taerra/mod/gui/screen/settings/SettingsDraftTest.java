package net.aerulion.taerra.mod.gui.screen.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsDraftTest {

  private static final List<ServerSettings.SettingInfo> SETTINGS =
      List.of(
          new ServerSettings.SettingInfo("web.name", "text", true, null, null, null),
          new ServerSettings.SettingInfo("web.port", "number", false, 1, 65535, null));
  private static final List<ServerSettings.WorldInfo> WORLDS =
      List.of(
          new ServerSettings.WorldInfo("a", "A", "NORMAL"),
          new ServerSettings.WorldInfo("b", "B", "NETHER"),
          new ServerSettings.WorldInfo("c", "C", "THE_END"));

  private static ServerSettings settings(final String values, final String author) {
    final JsonObject json = JsonParser.parseString(values).getAsJsonObject();
    return new ServerSettings(
        1,
        SETTINGS,
        WORLDS,
        List.of(),
        json,
        author == null ? null : Map.of("setting:web.name", new ServerSettings.Author(author, 0)),
        null);
  }

  private static ServerSettings server(final String name, final String order) {
    return server(name, order, null);
  }

  private static ServerSettings server(final String name, final String order, final String by) {
    return settings(
        """
        {"setting:web.name": "%s", "setting:web.port": 8123,
         "world:a:enabled": true, "world:b:enabled": true, "world:c:enabled": false,
         "order": %s}"""
            .formatted(name, order),
        by);
  }

  private static SettingsDraft opened() {
    final SettingsDraft draft = new SettingsDraft();
    draft.open(server("Old", "[\"a\", \"b\"]"));
    return draft;
  }

  @Test
  @DisplayName("a field nobody touched here follows the server and is reported as changed")
  void untouchedFollows() {
    final SettingsDraft draft = opened();
    final Set<String> changed = draft.rebase(server("New", "[\"a\", \"b\"]"));
    assertEquals(Set.of("setting:web.name"), changed);
    assertEquals(new JsonPrimitive("New"), draft.value("setting:web.name"));
    assertEquals(0, draft.conflicts());
    assertTrue(draft.changes().isEmpty());
  }

  @Test
  @DisplayName("an edit survives a rebase where the server kept the old value")
  void editSurvives() {
    final SettingsDraft draft = opened();
    draft.set("setting:web.name", new JsonPrimitive("Mine"));
    assertTrue(draft.rebase(server("Old", "[\"a\", \"b\"]")).isEmpty());
    assertEquals(new JsonPrimitive("Mine"), draft.value("setting:web.name"));
    assertEquals(0, draft.conflicts());
    assertEquals(Set.of("setting:web.name"), draft.changes().keySet());
  }

  @Test
  @DisplayName("an edit against a different server change is a conflict, resolved either way")
  void conflict() {
    final SettingsDraft draft = opened();
    draft.set("setting:web.name", new JsonPrimitive("Mine"));
    draft.rebase(server("Theirs", "[\"a\", \"b\"]", "Alex"));
    final SettingsDraft.Conflict conflict = draft.conflict("setting:web.name");
    assertNotNull(conflict);
    assertEquals(new JsonPrimitive("Theirs"), conflict.theirs());
    assertEquals("Alex", conflict.by());

    draft.useTheirs("setting:web.name");
    assertNull(draft.conflict("setting:web.name"));
    assertEquals(new JsonPrimitive("Theirs"), draft.value("setting:web.name"));
    assertFalse(draft.edited("setting:web.name"));
  }

  @Test
  @DisplayName("an edit the server made too is no conflict")
  void sameEdit() {
    final SettingsDraft draft = opened();
    draft.set("setting:web.name", new JsonPrimitive("Same"));
    draft.rebase(server("Same", "[\"a\", \"b\"]"));
    assertEquals(0, draft.conflicts());
    assertTrue(draft.changes().isEmpty());
  }

  @Test
  @DisplayName("settings the server marks read-only are never sent")
  void readOnly() {
    final SettingsDraft draft = opened();
    draft.set("setting:web.port", new JsonPrimitive(9000));
    assertTrue(draft.edited("setting:web.port"));
    assertTrue(draft.changes().isEmpty());
  }

  @Test
  @DisplayName("every world is listed, but the order only holds enabled ones")
  void order() {
    final SettingsDraft draft = opened();
    assertEquals(List.of("a", "b", "c"), draft.display());
    draft.move(0, 1);
    assertEquals(List.of("b", "a", "c"), draft.display());
    assertEquals(
        JsonParser.parseString("[\"b\", \"a\"]"), draft.changes().get(SettingValues.ORDER));
    draft.set("world:c:enabled", new JsonPrimitive(true));
    draft.fixOrder();
    assertEquals(JsonParser.parseString("[\"b\", \"a\", \"c\"]"), draft.value(SettingValues.ORDER));
  }

  @Test
  @DisplayName("a reorder that only moves a disabled world counts as no change")
  void pendingIgnoresSameOrder() {
    final SettingsDraft draft = opened();
    final JsonObject changes = new JsonObject();
    changes.add(SettingValues.ORDER, JsonParser.parseString("[\"a\", \"c\", \"b\"]"));
    assertEquals(0, draft.pending(changes));
  }

  @Test
  @DisplayName("fields the server no longer has leave the draft")
  void removedFields() {
    final SettingsDraft draft = opened();
    draft.rebase(settings("{\"setting:web.name\": \"Old\", \"order\": []}", null));
    assertNull(draft.value("world:a:enabled"));
  }
}
