package net.aerulion.taerra.util;

import java.util.concurrent.CompletionException;
import org.jetbrains.annotations.NotNull;

public final class Throwables {

  private Throwables() {
    super();
  }

  public static @NotNull Throwable unwrap(final @NotNull Throwable throwable) {
    return throwable instanceof CompletionException && throwable.getCause() != null
        ? throwable.getCause()
        : throwable;
  }

  public static @NotNull String describe(final @NotNull Throwable throwable) {
    final String message = throwable.getMessage();
    return message == null || message.isBlank()
        ? throwable.getClass().getSimpleName()
        : throwable.getClass().getSimpleName() + ": " + message;
  }
}
