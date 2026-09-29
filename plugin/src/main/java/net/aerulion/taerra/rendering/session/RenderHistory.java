package net.aerulion.taerra.rendering.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class RenderHistory {

  public static final int CAPACITY = 10;

  private final @NotNull Deque<CompletedRender> entries = new ArrayDeque<>();

  public synchronized void record(final @NotNull CompletedRender render) {
    this.entries.addFirst(render);
    while (this.entries.size() > CAPACITY) {
      this.entries.removeLast();
    }
  }

  public synchronized @NotNull List<CompletedRender> recent() {
    return new ArrayList<>(this.entries);
  }

  public synchronized boolean isEmpty() {
    return this.entries.isEmpty();
  }
}
