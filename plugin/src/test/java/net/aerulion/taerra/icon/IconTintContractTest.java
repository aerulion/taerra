package net.aerulion.taerra.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.Contracts;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class IconTintContractTest {

  @TestFactory
  List<DynamicTest> iconTint() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final JsonNode entry : Contracts.read("icon-tint")) {
      tests.add(
          DynamicTest.dynamicTest(
              entry.get("name").asText(),
              () -> {
                final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
                int index = 0;
                for (final JsonNode pixel : entry.get("pixels")) {
                  image.setRGB(index % 8, index / 8, Integer.parseUnsignedInt(pixel.asText(), 16));
                  index++;
                }
                assertEquals(entry.get("tinted").asBoolean(), MarkerIconsImpl.monochrome(image));
              }));
    }
    return tests;
  }
}
