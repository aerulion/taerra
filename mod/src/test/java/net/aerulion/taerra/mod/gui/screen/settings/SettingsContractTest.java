package net.aerulion.taerra.mod.gui.screen.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.aerulion.taerra.mod.Contracts;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class SettingsContractTest {

  private static ServerSettings settings(final JsonObject values, final JsonObject authors) {
    final List<ServerSettings.WorldInfo> worlds = new ArrayList<>();
    for (final String field : values.keySet()) {
      if (field.startsWith("world:") && field.endsWith(":enabled")) {
        final String id = SettingValues.worldOf(field);
        worlds.add(new ServerSettings.WorldInfo(id, id, null));
      }
    }
    final Map<String, ServerSettings.Author> by = new HashMap<>();
    if (authors != null) {
      authors
          .entrySet()
          .forEach(
              entry ->
                  by.put(
                      entry.getKey(),
                      new ServerSettings.Author(entry.getValue().getAsString(), 0)));
    }
    return new ServerSettings(1, List.of(), worlds, List.of(), values, by, null);
  }

  @TestFactory
  List<DynamicTest> layerScales() {
    final List<DynamicTest> tests = new ArrayList<>();
    final JsonObject cases = Contracts.read("layer-scales").getAsJsonObject();
    for (final JsonElement element : cases.getAsJsonArray("parse")) {
      final JsonObject entry = element.getAsJsonObject();
      final String text = entry.get("text").getAsString();
      tests.add(
          DynamicTest.dynamicTest(
              "reads \"" + text + "\"",
              () ->
                  assertEquals(
                      entry.get("scale").isJsonNull() ? null : entry.get("scale").getAsInt(),
                      SettingValues.parseScale(text))));
    }
    for (final JsonElement element : cases.getAsJsonArray("format")) {
      final JsonObject entry = element.getAsJsonObject();
      tests.add(
          DynamicTest.dynamicTest(
              "writes " + entry.get("scale").getAsInt(),
              () ->
                  assertEquals(
                      entry.get("text").getAsString(),
                      SettingValues.scaleText(entry.get("scale").getAsInt()))));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> worldLimits() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonElement element : Contracts.read("world-limits").getAsJsonArray()) {
      final JsonObject entry = element.getAsJsonObject();
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").getAsString(),
              () ->
                  assertEquals(
                      entry.get("valid").getAsBoolean(),
                      !SettingValues.badLimit(entry.get("limit")))));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> rebase() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonElement element : Contracts.read("settings-rebase").getAsJsonArray()) {
      final JsonObject entry = element.getAsJsonObject();
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").getAsString(),
              () -> {
                final SettingsDraft draft = new SettingsDraft();
                draft.open(settings(entry.getAsJsonObject("base"), null));
                entry
                    .getAsJsonObject("edits")
                    .entrySet()
                    .forEach(edit -> draft.set(edit.getKey(), edit.getValue()));
                final Set<String> changed =
                    draft.rebase(
                        settings(
                            entry.getAsJsonObject("now"),
                            entry.has("authors") ? entry.getAsJsonObject("authors") : null));
                final JsonObject expect = entry.getAsJsonObject("expect");
                expect
                    .getAsJsonObject("values")
                    .entrySet()
                    .forEach(
                        value ->
                            assertEquals(
                                value.getValue(), draft.value(value.getKey()), value.getKey()));
                if (expect.has("absent")) {
                  expect
                      .getAsJsonArray("absent")
                      .forEach(field -> assertNull(draft.value(field.getAsString())));
                }
                final JsonObject conflicts = new JsonObject();
                for (final String field :
                    new TreeSet<>(expect.getAsJsonObject("conflicts").keySet())) {
                  final SettingsDraft.Conflict conflict = draft.conflict(field);
                  if (conflict != null) {
                    final JsonObject json = new JsonObject();
                    json.add("theirs", conflict.theirs());
                    json.add(
                        "by",
                        conflict.by() == null
                            ? JsonNull.INSTANCE
                            : new JsonPrimitive(conflict.by()));
                    conflicts.add(field, json);
                  }
                }
                assertEquals(expect.getAsJsonObject("conflicts"), conflicts);
                assertEquals(expect.getAsJsonObject("conflicts").size(), draft.conflicts());
                final Set<String> wanted = new TreeSet<>();
                expect.getAsJsonArray("changed").forEach(field -> wanted.add(field.getAsString()));
                assertEquals(wanted, new TreeSet<>(changed));
              }));
    }
    return tests;
  }
}
