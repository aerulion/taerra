package net.aerulion.taerra.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public record StorageSettings(
    @NotNull String type,
    @NotNull String host,
    int port,
    @NotNull String name,
    @NotNull String user,
    @NotNull String password) {

  public static final @NotNull String FILES = "files";

  private static final @NotNull StorageSettings FILE_STORAGE =
      new StorageSettings(FILES, "", 0, "", "", "");

  public static @NotNull StorageSettings of(final @NotNull ConfigurationSection config) {
    final @NotNull StorageSettings settings =
        new StorageSettings(
            config.getString("storage.type", FILES),
            config.getString("storage.database.host", "localhost"),
            config.getInt("storage.database.port", 3306),
            config.getString("storage.database.name", "taerra"),
            config.getString("storage.database.user", ""),
            config.getString("storage.database.password", ""));
    return settings.database() ? settings : FILE_STORAGE;
  }

  public boolean database() {
    return !this.type.equals(FILES);
  }

  @Override
  public @NotNull String toString() {
    return database() ? this.type + " at " + this.host + ":" + this.port + "/" + this.name : FILES;
  }
}
