package net.aerulion.taerra.rendering.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderHistoryTest {

  private static @NotNull CompletedRender render(
      final String mode, final long durationMillis, final int tiles) {
    return new CompletedRender(mode, "world", "completed", tiles, 1, 100, durationMillis, 0L);
  }

  @Test
  @DisplayName("the newest finished render is reported first")
  void newestFirst() {
    final @NotNull RenderHistory history = new RenderHistory();
    history.record(render("first", 100, 1));
    history.record(render("second", 200, 2));

    final @NotNull List<CompletedRender> recent = history.recent();
    assertEquals("second", recent.get(0).mode());
    assertEquals("first", recent.get(1).mode());
  }

  @Test
  @DisplayName("only the most recent renders are kept, so the history cannot grow without bound")
  void oldestAreDropped() {
    final @NotNull RenderHistory history = new RenderHistory();
    for (int i = 0; i < RenderHistory.CAPACITY + 5; i++) {
      history.record(render("run" + i, 100, i));
    }
    final @NotNull List<CompletedRender> recent = history.recent();
    assertEquals(RenderHistory.CAPACITY, recent.size());
    assertEquals("run" + (RenderHistory.CAPACITY + 4), recent.getFirst().mode());
  }

  @Test
  @DisplayName("a fresh history reports nothing rather than a placeholder")
  void startsEmpty() {
    final @NotNull RenderHistory history = new RenderHistory();
    assertTrue(history.isEmpty());
    assertTrue(history.recent().isEmpty());
  }

  @Test
  @DisplayName("the reported list is a copy, so callers cannot corrupt the history")
  void recentIsDetached() {
    final @NotNull RenderHistory history = new RenderHistory();
    history.record(render("only", 100, 1));
    history.recent().clear();
    assertEquals(1, history.recent().size());
  }

  @Test
  @DisplayName("throughput is derived from the whole run, and an instant render reports none")
  void tilesPerSecond() {
    assertEquals(50.0, render("x", 2000, 100).tilesPerSecond(), 0.0001);
    assertEquals(0.0, render("x", 0, 100).tilesPerSecond(), 0.0001);
  }
}
