package net.aerulion.taerra.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class MainThread {

  private MainThread() {
    super();
  }

  public static <T> @NotNull CompletableFuture<T> supply(
      final @NotNull Plugin plugin, final @NotNull Supplier<T> task) {
    final @NotNull CompletableFuture<T> result = new CompletableFuture<>();
    plugin
        .getServer()
        .getScheduler()
        .runTask(
            plugin,
            () -> {
              try {
                result.complete(task.get());
              } catch (final RuntimeException e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  public static <T> @NotNull CompletableFuture<T> compose(
      final @NotNull Plugin plugin, final @NotNull Supplier<CompletableFuture<T>> task) {
    return supply(plugin, task).thenCompose(future -> future);
  }
}
