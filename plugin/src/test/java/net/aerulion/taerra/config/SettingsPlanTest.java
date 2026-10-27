package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.model.MapViews;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsPlanTest {

  private static final String OVERWORLD = "minecraft:overworld";
  private static final String NETHER = "minecraft:the_nether";
  private static final String END = "minecraft:the_end";

  private static Map<String, Object> current() {
    final Map<String, Object> values = new LinkedHashMap<>();
    values.put("setting:web.name", "");
    values.put("setting:web.port", 8123);
    values.put("setting:cache.max-tiles", 100);
    for (final String world : List.of(OVERWORLD, NETHER, END)) {
      values.put(ConfigEditor.worldField(world, "enabled"), !world.equals(END));
      values.put(ConfigEditor.worldField(world, "private"), false);
      values.put(ConfigEditor.worldField(world, "players"), false);
      values.put(ConfigEditor.worldField(world, "name"), "");
      values.put(ConfigEditor.worldField(world, ConfigEditor.LIMIT), Map.of("shape", "none"));
      values.put(ConfigEditor.worldField(world, ConfigEditor.VIEWS), "top_down");
    }
    values.put(ConfigEditor.ORDER, List.of(OVERWORLD, NETHER));
    values.put(ConfigEditor.LAYER_ICON + "spawn", "");
    return values;
  }

  private static SettingsRevisions revisions() {
    final SettingsRevisions revisions = new SettingsRevisions();
    revisions.seed(current(), 1000);
    return revisions;
  }

  private static SettingsPlan.Result plan(final Map<String, Object> changes) {
    return plan(changes, new FakeServer());
  }

  private static SettingsPlan.Result plan(
      final Map<String, Object> changes, final FakeServer server) {
    final SettingsRevisions revisions = revisions();
    return SettingsPlan.plan(revisions.revision(), changes, current(), revisions, server);
  }

  private static Map<String, Object> limit(final int size) {
    return Map.of("shape", "rectangle", "minX", -size, "minZ", -size, "maxX", size, "maxZ", size);
  }

  @Test
  @DisplayName("unknown fields, read-only settings and bad values are refused by field")
  void invalid() {
    assertEquals(new SettingsPlan.Invalid("setting:nope"), plan(Map.of("setting:nope", 1)));
    assertEquals(
        new SettingsPlan.Invalid("setting:web.port"), plan(Map.of("setting:web.port", 9000)));
    assertEquals(
        new SettingsPlan.Invalid("setting:cache.max-tiles"),
        plan(Map.of("setting:cache.max-tiles", -1)));
    assertEquals(
        new SettingsPlan.Invalid(ConfigEditor.LAYER_ICON + "spawn"),
        plan(Map.of(ConfigEditor.LAYER_ICON + "spawn", "taerra:missing")));
    assertEquals(
        new SettingsPlan.Invalid(ConfigEditor.ORDER),
        plan(Map.of(ConfigEditor.ORDER, List.of(OVERWORLD, OVERWORLD))));
  }

  @Test
  @DisplayName("a field someone else changed since the page loaded is a conflict, unless equal")
  void conflicts() {
    final SettingsRevisions revisions = revisions();
    final long loaded = revisions.revision();
    final Map<String, Object> now = current();
    now.put("setting:web.name", "Theirs");
    revisions.track(now, "Alex", 2000);

    final SettingsPlan.Result clash =
        SettingsPlan.plan(
            loaded, Map.of("setting:web.name", "Mine"), now, revisions, new FakeServer());
    final SettingsPlan.Conflicted conflicted =
        assertInstanceOf(SettingsPlan.Conflicted.class, clash);
    assertEquals("setting:web.name", conflicted.conflicts().getFirst().field());
    assertEquals("Theirs", conflicted.conflicts().getFirst().value());
    assertEquals("Alex", conflicted.conflicts().getFirst().by().name());

    final SettingsPlan.Result same =
        SettingsPlan.plan(
            loaded, Map.of("setting:web.name", "Theirs"), now, revisions, new FakeServer());
    assertTrue(assertInstanceOf(SettingsPlan.Ready.class, same).next().isEmpty());
  }

  @Test
  @DisplayName("unchanged fields drop out, so saving an untouched page writes nothing")
  void unchanged() {
    final SettingsPlan.Ready ready =
        assertInstanceOf(SettingsPlan.Ready.class, plan(Map.of("setting:cache.max-tiles", 100)));
    assertTrue(ready.next().isEmpty());
  }

  @Test
  @DisplayName("only one change that needs a render goes through per save")
  void oneRender() {
    final String limit = ConfigEditor.worldField(OVERWORLD, ConfigEditor.LIMIT);
    final String views = ConfigEditor.worldField(NETHER, ConfigEditor.VIEWS);
    final Map<String, Object> both = new LinkedHashMap<>();
    both.put(limit, limit(100));
    both.put(views, "both");
    assertEquals(new SettingsPlan.Invalid(views), plan(both));

    final Map<String, Object> narrowing = new LinkedHashMap<>();
    narrowing.put(limit, limit(100));
    narrowing.put(views, "top_down");
    narrowing.put(ConfigEditor.worldField(OVERWORLD, ConfigEditor.VIEWS), "top_down");
    assertInstanceOf(SettingsPlan.Ready.class, plan(narrowing));
  }

  @Test
  @DisplayName("limits and views wait while a render runs")
  void busy() {
    final FakeServer rendering = new FakeServer();
    rendering.rendering = true;
    final String views = ConfigEditor.worldField(NETHER, ConfigEditor.VIEWS);
    assertEquals(new SettingsPlan.Busy(views), plan(Map.of(views, "isometric"), rendering));
    final String limit = ConfigEditor.worldField(OVERWORLD, ConfigEditor.LIMIT);
    assertEquals(new SettingsPlan.Busy(limit), plan(Map.of(limit, limit(50)), rendering));
    assertInstanceOf(
        SettingsPlan.Ready.class, plan(Map.of("setting:web.name", "Server"), rendering));
  }

  @Test
  @DisplayName("a limit needs a loaded world that is on the map, or is put on it in the same save")
  void limitNeedsEnabledWorld() {
    final String limit = ConfigEditor.worldField(END, ConfigEditor.LIMIT);
    assertEquals(new SettingsPlan.Invalid(limit), plan(Map.of(limit, limit(50))));

    final Map<String, Object> enabling = new LinkedHashMap<>();
    enabling.put(ConfigEditor.worldField(END, "enabled"), true);
    enabling.put(limit, limit(50));
    assertInstanceOf(SettingsPlan.Ready.class, plan(enabling));

    final FakeServer unloaded = new FakeServer();
    unloaded.loaded.remove(OVERWORLD);
    final String overworld = ConfigEditor.worldField(OVERWORLD, ConfigEditor.LIMIT);
    assertEquals(new SettingsPlan.Invalid(overworld), plan(Map.of(overworld, limit(50)), unloaded));
  }

  @Test
  @DisplayName("world changes rebuild the lists: enabled worlds join the order, names reset")
  void worlds() {
    final Map<String, Object> changes = new LinkedHashMap<>();
    changes.put(ConfigEditor.worldField(END, "enabled"), true);
    changes.put(ConfigEditor.worldField(NETHER, "private"), true);
    changes.put(ConfigEditor.worldField(OVERWORLD, "name"), "Home");
    changes.put(ConfigEditor.ORDER, List.of(NETHER, OVERWORLD));
    final SettingsPlan.Ready ready = assertInstanceOf(SettingsPlan.Ready.class, plan(changes));

    final Map<String, String> names = new HashMap<>(Map.of(NETHER, "Old"));
    final SettingsPlan.Worlds worlds =
        SettingsPlan.worlds(
            ready, List.of(OVERWORLD, NETHER), new LinkedHashSet<>(), new LinkedHashSet<>(), names);
    assertNotNull(worlds);
    assertEquals(List.of(NETHER, OVERWORLD, END), worlds.order());
    assertEquals(Set.of(NETHER), worlds.hidden());
    assertEquals(Map.of(NETHER, "Old", OVERWORLD, "Home"), worlds.names());

    final SettingsPlan.Ready nothing =
        assertInstanceOf(SettingsPlan.Ready.class, plan(Map.of("setting:web.name", "X")));
    assertNull(
        SettingsPlan.worlds(
            nothing, List.of(OVERWORLD), new LinkedHashSet<>(), new LinkedHashSet<>(), Map.of()));
  }

  @Test
  @DisplayName("a world name equal to its default name is stored as no name")
  void worldNames() {
    assertEquals("", SettingsPlan.worldName(OVERWORLD, "  "));
    assertEquals("Home", SettingsPlan.worldName(OVERWORLD, "Home"));
    assertNull(SettingsPlan.worldName("Not A Key", "Home"));
  }

  private static final class FakeServer implements SettingsPlan.Server {

    private final Set<String> loaded = new LinkedHashSet<>(List.of(OVERWORLD, NETHER, END));
    private final Set<String> enabled = Set.of(OVERWORLD, NETHER);
    private boolean rendering;

    @Override
    public boolean worldLoaded(final @NotNull String world) {
      return this.loaded.contains(world);
    }

    @Override
    public boolean worldEnabled(final @NotNull String world) {
      return this.enabled.contains(world);
    }

    @Override
    public @NotNull MapViews views(final @NotNull String world) {
      return MapViews.TOP_DOWN;
    }

    @Override
    public boolean iconExists(final @NotNull String icon) {
      return icon.equals("taerra:marker");
    }

    @Override
    public boolean rendering() {
      return this.rendering;
    }
  }
}
