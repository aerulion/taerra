package net.aerulion.taerra.web.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WebSessions {

  public static final @NotNull String COOKIE = "taerra_session";
  public static final @NotNull Duration SESSION_LIFETIME = Duration.ofDays(30);
  private static final @NotNull Duration CODE_LIFETIME = Duration.ofMinutes(5);
  private static final int TOKEN_BYTES = 32;

  private final @NotNull File file;
  private final @NotNull Logger logger;
  private final @NotNull SecureRandom random = new SecureRandom();
  private final @NotNull Map<String, Session> codes = new HashMap<>();
  private final @NotNull Map<String, Session> sessions = new HashMap<>();
  private final @NotNull Map<UUID, Set<String>> grants = new HashMap<>();
  // Keys of sessions that live only as long as this run, never written to sessions.yml.
  private final @NotNull Set<String> unsaved = new HashSet<>();
  private @NotNull Runnable onChange = () -> {};

  public WebSessions(final @NotNull File file, final @NotNull Logger logger) {
    super();
    this.file = file;
    this.logger = logger;
  }

  private static @NotNull String hash(final @NotNull String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is missing from the JVM", e);
    }
  }

  private @NotNull String token() {
    final byte @NotNull [] bytes = new byte[TOKEN_BYTES];
    this.random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public synchronized void onChange(final @NotNull Runnable listener) {
    this.onChange = listener;
  }

  public synchronized void load() {
    final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
    final @Nullable ConfigurationSection granted = yaml.getConfigurationSection("grants");
    if (granted != null) {
      for (final @NotNull String uuid : granted.getKeys(false)) {
        try {
          this.grants.put(UUID.fromString(uuid), Set.copyOf(granted.getStringList(uuid)));
        } catch (final IllegalArgumentException e) {
          this.logger.warning("Skipping malformed grants in sessions.yml: " + uuid);
        }
      }
    }
    final @Nullable ConfigurationSection section = yaml.getConfigurationSection("sessions");
    if (section == null) {
      return;
    }
    final long now = System.currentTimeMillis();
    for (final @NotNull String key : section.getKeys(false)) {
      final @Nullable String uuid = section.getString(key + ".uuid");
      final @Nullable String name = section.getString(key + ".name");
      final @Nullable String skin = section.getString(key + ".skin");
      final long expires = section.getLong(key + ".expires");
      if (uuid == null || name == null || expires < now) {
        continue;
      }
      try {
        this.sessions.put(key, new Session(UUID.fromString(uuid), name, skin, expires));
      } catch (final IllegalArgumentException e) {
        this.logger.warning("Skipping a malformed session in sessions.yml: " + key);
      }
    }
  }

  public synchronized @NotNull String createCode(
      final @NotNull UUID uuid, final @NotNull String name, final @Nullable String skin) {
    final long now = System.currentTimeMillis();
    this.codes.values().removeIf(code -> code.expires() < now);
    final @NotNull String code = token();
    this.codes.put(hash(code), new Session(uuid, name, skin, now + CODE_LIFETIME.toMillis()));
    return code;
  }

  public synchronized @Nullable Login redeem(final @NotNull String code) {
    final long now = System.currentTimeMillis();
    final @Nullable Session pending = this.codes.remove(hash(code));
    if (pending == null || pending.expires() < now) {
      return null;
    }
    final @NotNull String token = token();
    final @NotNull Session session =
        new Session(
            pending.uuid(), pending.name(), pending.skin(), now + SESSION_LIFETIME.toMillis());
    this.sessions.put(hash(token), session);
    save();
    return new Login(token, session);
  }

  /**
   * Opens a session without a login code that is never written to disk, for the mod: it ends when
   * the player quits, and a crash must not leave it valid.
   */
  public synchronized @NotNull String open(
      final @NotNull UUID uuid, final @NotNull String name, final @Nullable String skin) {
    final @NotNull String token = token();
    final @NotNull String key = hash(token);
    this.sessions.put(
        key,
        new Session(uuid, name, skin, System.currentTimeMillis() + SESSION_LIFETIME.toMillis()));
    this.unsaved.add(key);
    return token;
  }

  public synchronized @Nullable Session session(final @Nullable String token) {
    if (token == null) {
      return null;
    }
    final @Nullable Session session = this.sessions.get(hash(token));
    return session == null || session.expires() < System.currentTimeMillis() ? null : session;
  }

  public synchronized @NotNull Viewer viewer(final @Nullable String token) {
    final @Nullable Session session = session(token);
    return session == null
        ? Viewer.ANONYMOUS
        : new Viewer(session.uuid(), this.grants.getOrDefault(session.uuid(), Set.of()));
  }

  public synchronized @NotNull Map<String, UUID> accountNames() {
    final @NotNull Map<String, UUID> names = new HashMap<>();
    this.sessions.values().stream()
        .sorted(Comparator.comparingLong(Session::expires))
        .forEach(session -> names.put(session.name(), session.uuid()));
    return names;
  }

  public synchronized @NotNull Set<UUID> accounts() {
    return this.sessions.values().stream().map(Session::uuid).collect(Collectors.toSet());
  }

  public synchronized void grant(final @NotNull UUID uuid, final @NotNull Set<String> granted) {
    if (!Objects.equals(this.grants.get(uuid), granted)) {
      this.grants.put(uuid, Set.copyOf(granted));
      save();
      this.onChange.run();
    }
  }

  public synchronized void end(final @Nullable String token) {
    if (token == null) {
      return;
    }
    final @NotNull String key = hash(token);
    if (this.sessions.remove(key) != null) {
      if (!this.unsaved.remove(key)) {
        save();
      }
      this.onChange.run();
    }
  }

  public synchronized int endAll(final @NotNull UUID uuid) {
    final int before = this.sessions.size();
    this.sessions.values().removeIf(session -> session.uuid().equals(uuid));
    final int ended = before - this.sessions.size();
    if (ended > 0) {
      save();
      this.onChange.run();
    }
    return ended;
  }

  private void save() {
    final long now = System.currentTimeMillis();
    this.sessions.values().removeIf(session -> session.expires() < now);
    this.unsaved.retainAll(this.sessions.keySet());
    this.grants
        .keySet()
        .removeIf(
            uuid ->
                this.sessions.values().stream().noneMatch(session -> session.uuid().equals(uuid))
                    && this.codes.values().stream().noneMatch(code -> code.uuid().equals(uuid)));
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    this.grants.forEach(
        (uuid, granted) -> yaml.set("grants." + uuid, granted.stream().sorted().toList()));
    this.sessions.forEach(
        (key, session) -> {
          if (this.unsaved.contains(key)) {
            return;
          }
          yaml.set("sessions." + key + ".uuid", session.uuid().toString());
          yaml.set("sessions." + key + ".name", session.name());
          yaml.set("sessions." + key + ".skin", session.skin());
          yaml.set("sessions." + key + ".expires", session.expires());
        });
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.logger.log(Level.SEVERE, "Failed to save sessions.yml", e);
    }
  }

  public record Session(
      @NotNull UUID uuid, @NotNull String name, @Nullable String skin, long expires) {}

  public record Login(@NotNull String token, @NotNull Session session) {}
}
