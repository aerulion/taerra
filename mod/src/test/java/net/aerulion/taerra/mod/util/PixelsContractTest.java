package net.aerulion.taerra.mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.mod.Contracts;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class PixelsContractTest {

  @TestFactory
  List<DynamicTest> iconTint() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonElement element : Contracts.read("icon-tint").getAsJsonArray()) {
      final JsonObject entry = element.getAsJsonObject();
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").getAsString(),
              () -> {
                final List<JsonElement> pixels = entry.getAsJsonArray("pixels").asList();
                final int[] argb = new int[pixels.size()];
                for (int i = 0; i < argb.length; i++) {
                  argb[i] = Integer.parseUnsignedInt(pixels.get(i).getAsString(), 16);
                }
                assertEquals(entry.get("tinted").getAsBoolean(), Pixels.monochrome(argb));
              }));
    }
    return tests;
  }
}
