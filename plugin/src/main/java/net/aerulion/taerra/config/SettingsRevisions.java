package net.aerulion.taerra.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingsRevisions {

  private final @NotNull Map<String, Long> revisions = new HashMap<>();
  private final @NotNull Map<String, Author> authors = new HashMap<>();
  private @NotNull Map<String, Object> published = Map.of();
  private long revision;

  synchronized void seed(final @NotNull Map<String, Object> values, final long now) {
    this.published = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    this.revision = Math.max(this.revision, now);
  }

  synchronized @NotNull Set<String> track(
      final @NotNull Map<String, Object> values, final @Nullable String who, final long now) {
    final @NotNull Set<String> fields = new HashSet<>(this.published.keySet());
    fields.addAll(values.keySet());
    fields.removeIf(field -> Objects.equals(this.published.get(field), values.get(field)));
    if (fields.isEmpty()) {
      return Set.of();
    }
    this.revision = Math.max(this.revision + 1, now);
    for (final @NotNull String field : fields) {
      this.revisions.put(field, this.revision);
      this.authors.put(field, new Author(who, now));
    }
    this.published = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    return Set.copyOf(fields);
  }

  synchronized long revision() {
    return this.revision;
  }

  synchronized boolean changedSince(final @NotNull String field, final long base) {
    return this.revisions.getOrDefault(field, 0L) > base;
  }

  synchronized @Nullable Author author(final @NotNull String field) {
    return this.authors.get(field);
  }

  synchronized @NotNull Map<String, Author> authors() {
    return Map.copyOf(this.authors);
  }

  public record Author(@Nullable String name, long at) {}
}
