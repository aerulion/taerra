package net.aerulion.taerra.util;

import org.jetbrains.annotations.NotNull;

public final class Throwables {

  private Throwables() {
    super();
  }

  public static @NotNull String describe(final @NotNull Throwable throwable) {
    final String message = throwable.getMessage();
    return message == null || message.isBlank()
        ? throwable.getClass().getSimpleName()
        : throwable.getClass().getSimpleName() + ": " + message;
  }
}
