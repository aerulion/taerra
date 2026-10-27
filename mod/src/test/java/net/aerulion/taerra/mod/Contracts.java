package net.aerulion.taerra.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Contracts {

  private Contracts() {}

  public static JsonElement read(final String name) {
    try {
      return JsonParser.parseString(
          Files.readString(Path.of("../plugin/src/test/resources/contracts", name + ".json")));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
