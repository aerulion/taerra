package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.Contracts;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class SettingContractTest {

  @TestFactory
  List<DynamicTest> layerScales() {
    final List<DynamicTest> tests = new ArrayList<>();
    final JsonNode cases = Contracts.read("layer-scales");
    for (final JsonNode entry : cases.get("parse")) {
      final String text = entry.get("text").asText();
      tests.add(
          DynamicTest.dynamicTest(
              "reads \"" + text + "\"",
              () ->
                  assertEquals(
                      entry.get("scale").isNull() ? null : entry.get("scale").asInt(),
                      LayerScale.parse(text))));
    }
    for (final JsonNode entry : cases.get("format")) {
      tests.add(
          DynamicTest.dynamicTest(
              "writes " + entry.get("scale").asInt(),
              () ->
                  assertEquals(
                      entry.get("text").asText(), LayerScale.format(entry.get("scale").asInt()))));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> worldLimits() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonNode entry : Contracts.read("world-limits")) {
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").asText(),
              () ->
                  assertEquals(
                      entry.get("valid").asBoolean(),
                      ConfigEditor.normalizeLimit(Contracts.convert(entry.get("limit"), Map.class))
                          != null)));
    }
    return tests;
  }
}
