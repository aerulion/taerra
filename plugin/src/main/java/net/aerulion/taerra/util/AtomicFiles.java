package net.aerulion.taerra.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jetbrains.annotations.NotNull;

public final class AtomicFiles {

  private AtomicFiles() {
    super();
  }

  public static void write(final @NotNull File file, final byte @NotNull [] data)
      throws IOException {
    file.getParentFile().mkdirs();
    final @NotNull Path target = file.toPath();
    final @NotNull Path tempPath =
        target.resolveSibling(
            target.getFileName() + "." + Thread.currentThread().threadId() + ".tmp");
    try {
      Files.write(tempPath, data);
      try {
        Files.move(
            tempPath, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (final AtomicMoveNotSupportedException e) {
        Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tempPath);
    }
  }
}
