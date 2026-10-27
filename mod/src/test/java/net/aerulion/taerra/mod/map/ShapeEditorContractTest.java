package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.mod.Contracts;
import net.aerulion.taerra.mod.web.model.Regions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ShapeEditorContractTest {

  @TestFactory
  List<DynamicTest> regionColors() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonElement element : Contracts.read("region-colors").getAsJsonArray()) {
      final JsonObject entry = element.getAsJsonObject();
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").getAsString(),
              () -> {
                final List<Regions.Region> regions = new ArrayList<>();
                for (final JsonElement color : entry.getAsJsonArray("used")) {
                  regions.add(
                      new Regions.Region(
                          "r" + regions.size(),
                          "",
                          new int[] {0, 0},
                          color.getAsString(),
                          null,
                          null,
                          null,
                          null));
                }
                assertEquals(
                    entry.get("fresh").getAsString(),
                    ShapeEditor.freshColor(new Regions(List.of(), regions, true)));
              }));
    }
    return tests;
  }
}
