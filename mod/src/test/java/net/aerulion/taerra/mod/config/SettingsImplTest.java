package net.aerulion.taerra.mod.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsImplTest {

  @Test
  void onlyWritesWhenSomethingChanged(@TempDir final @NotNull Path directory) throws Exception {
    final @NotNull Path file = directory.resolve("taerra.json");
    final @NotNull Settings settings = SettingsImpl.load(file);
    settings.save();
    assertTrue(Files.exists(file));

    Files.delete(file);
    settings.save();
    assertFalse(Files.exists(file), "unchanged settings are not written again");

    settings.setGrayMap(!settings.grayMap());
    settings.save();
    assertTrue(Files.exists(file));
  }

  @Test
  void aReloadedFileIsNotRewritten(@TempDir final @NotNull Path directory) throws Exception {
    final @NotNull Path file = directory.resolve("taerra.json");
    SettingsImpl.load(file).save();
    final @NotNull Settings reloaded = SettingsImpl.load(file);
    Files.delete(file);
    reloaded.save();
    assertFalse(Files.exists(file));
  }
}
