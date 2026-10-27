package net.aerulion.taerra.mod.gui.screen.settings;

import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.ORDER;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.array;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.ids;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.on;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.same;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.sameOrder;
import static net.aerulion.taerra.mod.gui.screen.settings.SettingValues.worldField;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.aerulion.taerra.mod.web.model.ServerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SettingsDraft {

  private final Map<String, JsonElement> base = new LinkedHashMap<>();
  private final Map<String, JsonElement> draft = new LinkedHashMap<>();
  private final Map<String, Conflict> conflicts = new LinkedHashMap<>();
  private final List<String> display = new ArrayList<>();
  private @Nullable ServerSettings view;

  private static @Nullable String author(
      final @NotNull ServerSettings next, final @NotNull String field) {
    final ServerSettings.@Nullable Author author =
        next.authors() == null ? null : next.authors().get(field);
    return author == null ? null : author.name();
  }

  public @Nullable ServerSettings view() {
    return this.view;
  }

  public void open(final @NotNull ServerSettings next) {
    this.view = next;
    this.base.clear();
    this.draft.clear();
    this.conflicts.clear();
    next.values().entrySet().forEach(entry -> this.base.put(entry.getKey(), entry.getValue()));
    this.base.forEach((field, value) -> this.draft.put(field, value.deepCopy()));
    this.display.clear();
    arrange(ids(this.draft.get(ORDER)));
  }

  public @NotNull Set<String> rebase(final @NotNull ServerSettings next) {
    final @NotNull Map<String, JsonElement> now = new LinkedHashMap<>();
    next.values().entrySet().forEach(entry -> now.put(entry.getKey(), entry.getValue()));
    final @NotNull Set<String> fields = new LinkedHashSet<>(this.base.keySet());
    fields.addAll(now.keySet());
    fields.addAll(this.draft.keySet());
    final @NotNull Set<String> changed = new LinkedHashSet<>();
    for (final @NotNull String field : fields) {
      if (!now.containsKey(field)) {
        this.draft.remove(field);
        this.conflicts.remove(field);
        continue;
      }
      if (field.equals(ORDER)) {
        continue;
      }
      final @Nullable JsonElement mine = this.draft.get(field);
      final boolean touched = this.draft.containsKey(field) && !same(mine, this.base.get(field));
      if (!touched) {
        if (this.base.containsKey(field) && !same(mine, now.get(field))) {
          changed.add(field);
        }
        this.draft.put(field, now.get(field).deepCopy());
        this.conflicts.remove(field);
      } else if (same(mine, now.get(field))) {
        this.conflicts.remove(field);
      } else if (!same(this.base.get(field), now.get(field))) {
        this.conflicts.put(field, new Conflict(now.get(field).deepCopy(), author(next, field)));
      }
    }
    this.view = next;
    final @NotNull JsonElement theirs =
        Objects.requireNonNullElse(now.get(ORDER), JsonNull.INSTANCE);
    if (same(this.draft.get(ORDER), this.base.get(ORDER))) {
      if (!sameOrder(this.draft.get(ORDER), theirs)) {
        changed.add(ORDER);
      }
      this.draft.put(ORDER, theirs.deepCopy());
      this.conflicts.remove(ORDER);
    } else if (same(this.draft.get(ORDER), theirs)) {
      this.conflicts.remove(ORDER);
    } else if (!sameOrder(this.base.get(ORDER), theirs)) {
      this.conflicts.put(ORDER, new Conflict(theirs.deepCopy(), author(next, ORDER)));
    }
    arrange(ids(this.draft.get(ORDER)));
    fixOrder();
    this.base.clear();
    this.base.putAll(now);
    return changed;
  }

  public @Nullable JsonElement value(final @NotNull String field) {
    return this.draft.get(field);
  }

  public @Nullable JsonElement saved(final @NotNull String field) {
    return this.base.get(field);
  }

  public boolean edited(final @NotNull String field) {
    return !same(this.draft.get(field), this.base.get(field));
  }

  public void set(final @NotNull String field, final @NotNull JsonElement value) {
    this.draft.put(field, value);
    this.conflicts.remove(field);
  }

  public void saved(final @NotNull JsonObject changes) {
    changes.entrySet().forEach(entry -> this.base.put(entry.getKey(), entry.getValue()));
  }

  public @Nullable Conflict conflict(final @NotNull String field) {
    return this.conflicts.get(field);
  }

  public int conflicts() {
    return this.conflicts.size();
  }

  public void keepMine(final @NotNull String field) {
    this.conflicts.remove(field);
  }

  public void useTheirs(final @NotNull String field) {
    final @Nullable Conflict conflict = this.conflicts.remove(field);
    if (conflict == null) {
      return;
    }
    this.draft.put(field, conflict.theirs().deepCopy());
    if (field.equals(ORDER)) {
      arrange(ids(conflict.theirs()));
    }
    if (field.equals(ORDER) || field.endsWith(":enabled")) {
      fixOrder();
    }
  }

  public @NotNull List<String> display() {
    return Collections.unmodifiableList(this.display);
  }

  public void move(final int index, final int by) {
    final int target = index + by;
    this.display.set(index, this.display.set(target, this.display.get(index)));
    fixOrder();
    this.conflicts.remove(ORDER);
  }

  public void fixOrder() {
    this.draft.put(
        ORDER,
        array(
            this.display.stream()
                .filter(id -> on(this.draft.get(worldField(id, "enabled"))))
                .toList()));
  }

  public @NotNull JsonObject changes() {
    final @NotNull JsonObject changes = new JsonObject();
    this.draft.forEach(
        (field, value) -> {
          if (field.equals(ORDER)) {
            if (!same(value, this.base.get(ORDER))) {
              changes.add(ORDER, value);
            }
            return;
          }
          if (same(value, this.base.get(field))) {
            return;
          }
          final ServerSettings.@Nullable SettingInfo setting =
              field.startsWith("setting:") ? setting(field.substring("setting:".length())) : null;
          if (setting == null || setting.editable()) {
            changes.add(field, value);
          }
        });
    return changes;
  }

  public int pending(final @NotNull JsonObject changes) {
    return changes.size()
        - (changes.has(ORDER) && sameOrder(changes.get(ORDER), this.base.get(ORDER)) ? 1 : 0);
  }

  public ServerSettings.@Nullable SettingInfo setting(final @NotNull String key) {
    return this.view == null
        ? null
        : this.view.settings().stream()
            .filter(setting -> setting.key().equals(key))
            .findFirst()
            .orElse(null);
  }

  public ServerSettings.@Nullable WorldInfo world(final @NotNull String id) {
    return this.view == null
        ? null
        : this.view.worlds().stream()
            .filter(world -> world.id().equals(id))
            .findFirst()
            .orElse(null);
  }

  private void arrange(final @NotNull List<String> order) {
    if (this.view == null) {
      return;
    }
    final @NotNull Set<String> known = new LinkedHashSet<>();
    this.view.worlds().forEach(world -> known.add(world.id()));
    final @NotNull List<String> wanted = order.stream().filter(known::contains).toList();
    final @NotNull List<String> queue = new ArrayList<>(wanted);
    final @NotNull LinkedHashSet<String> next = new LinkedHashSet<>();
    for (final @NotNull String id : this.display) {
      if (known.contains(id)) {
        next.add(wanted.contains(id) ? queue.removeFirst() : id);
      }
    }
    next.addAll(queue);
    next.addAll(known);
    this.display.clear();
    this.display.addAll(next);
  }

  public record Conflict(@NotNull JsonElement theirs, @Nullable String by) {}
}
