package net.aerulion.taerra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

public final class Contracts {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Contracts() {}

  public static JsonNode read(final String name) {
    try (final InputStream in =
        Objects.requireNonNull(
            Contracts.class.getClassLoader().getResourceAsStream("contracts/" + name + ".json"),
            name)) {
      return MAPPER.readTree(in);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T convert(final JsonNode node, final Class<T> type) {
    return MAPPER.convertValue(node, type);
  }
}
