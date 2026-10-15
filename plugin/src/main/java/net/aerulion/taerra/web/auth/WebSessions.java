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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
  public static final @NotNull Duration CODE_LIFETIME = Duration.ofMinutes(5);
  private static final int TOKEN_BYTES = 32;
  private static final @NotNull String PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int PAIR_LENGTH = 6;
  static final int MAX_PAIRINGS = 10_000;
  private static final int MAX_SESSIONS = 10;
  private static final int MAX_FAILED_APPROVALS = 5;
  private static final @NotNull Duration APPROVAL_WINDOW = Duration.ofMinutes(1);
  private static final @NotNull Duration SEEN_STEP = Duration.ofMinutes(1);
  private static final @NotNull Duration SEEN_SAVE = Duration.ofMinutes(10);
  private static final int ID_LENGTH = 12;

  private final @NotNull File file;
  private final @NotNull Logger logger;
  private final @NotNull SecureRandom random = new SecureRandom();
  private final @NotNull Map<String, Code> codes = new HashMap<>();
  private final @NotNull Map<String, Pairing> pairings = new LinkedHashMap<>();
  private final @NotNull Map<UUID, Failures> failures = new HashMap<>();
  private final @NotNull Map<String, Session> sessions = new HashMap<>();
  private final @NotNull Map<UUID, Set<String>> grants = new HashMap<>();
  private final @NotNull Set<String> unsaved = new HashSet<>();
  private final @NotNull Map<String, Long> seen = new HashMap<>();
  private long seenSaved;
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
      final long created = section.getLong(key + ".created", expires - SESSION_LIFETIME.toMillis());
      try {
        this.sessions.put(
            key,
            new Session(
                UUID.fromString(uuid),
                name,
                skin,
                expires,
                created,
                new Device(section.getString(key + ".browser"), section.getString(key + ".os"))));
        this.seen.put(key, section.getLong(key + ".seen", created));
      } catch (final IllegalArgumentException e) {
        this.logger.warning("Skipping a malformed session in sessions.yml: " + key);
      }
    }
  }

  public static @Nullable String pairCode(final @NotNull String typed) {
    final @NotNull String code = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    return code.length() == PAIR_LENGTH
            && code.chars().allMatch(character -> PAIR_ALPHABET.indexOf(character) >= 0)
        ? code
        : null;
  }

  public synchronized @NotNull String createCode(
      final @NotNull UUID uuid, final @NotNull String name, final @Nullable String skin) {
    return createCode(new Session(uuid, name, skin, 0, 0, null), Long.MAX_VALUE);
  }

  private @NotNull String createCode(final @NotNull Session who, final long until) {
    final long now = System.currentTimeMillis();
    this.codes
        .values()
        .removeIf(code -> code.expires() < now || code.who().uuid().equals(who.uuid()));
    final @NotNull String code = token();
    this.codes.put(hash(code), new Code(who, now + CODE_LIFETIME.toMillis(), until));
    return code;
  }

  public synchronized @Nullable String linkCode(final @Nullable String token) {
    final @Nullable Session session = session(token);
    return session == null || this.unsaved.contains(hash(token))
        ? null
        : createCode(session, session.expires());
  }

  public synchronized @Nullable Login redeem(
      final @NotNull String code, final @NotNull Device device) {
    final long now = System.currentTimeMillis();
    final @Nullable Code pending = this.codes.remove(hash(code));
    if (pending == null || pending.expires() < now) {
      return null;
    }
    return login(
        pending.who(), Math.min(now + SESSION_LIFETIME.toMillis(), pending.until()), device);
  }

  private @NotNull Login login(
      final @NotNull Session who, final long expires, final @NotNull Device device) {
    final @NotNull String token = token();
    final long now = System.currentTimeMillis();
    final @NotNull Session session =
        new Session(who.uuid(), who.name(), who.skin(), expires, now, device);
    final @NotNull String key = hash(token);
    this.sessions.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().uuid().equals(who.uuid())
                    && !this.unsaved.contains(entry.getKey()))
        .sorted(Comparator.comparingLong(entry -> -entry.getValue().expires()))
        .skip(MAX_SESSIONS - 1)
        .map(Map.Entry::getKey)
        .toList()
        .forEach(this.sessions::remove);
    this.sessions.put(key, session);
    this.seen.put(key, now);
    save();
    return new Login(token, session);
  }

  public synchronized @NotNull Pair pair() {
    final long now = System.currentTimeMillis();
    this.pairings.values().removeIf(pairing -> pairing.expires() < now);
    if (this.pairings.size() >= MAX_PAIRINGS) {
      this.pairings.remove(this.pairings.keySet().iterator().next());
    }
    @NotNull String code;
    do {
      final @NotNull StringBuilder built = new StringBuilder(PAIR_LENGTH);
      for (int i = 0; i < PAIR_LENGTH; i++) {
        built.append(PAIR_ALPHABET.charAt(this.random.nextInt(PAIR_ALPHABET.length())));
      }
      code = built.toString();
    } while (this.pairings.containsKey(code));
    final @NotNull String claim = token();
    this.pairings.put(code, new Pairing(hash(claim), now + CODE_LIFETIME.toMillis(), null));
    return new Pair(code, claim);
  }

  public synchronized @NotNull Approval approve(
      final @Nullable String code,
      final @NotNull UUID uuid,
      final @NotNull String name,
      final @Nullable String skin) {
    final long now = System.currentTimeMillis();
    this.failures.values().removeIf(failed -> failed.until() < now);
    final @Nullable Failures failed = this.failures.get(uuid);
    if (failed != null && failed.count() >= MAX_FAILED_APPROVALS) {
      return Approval.TOO_MANY;
    }
    final @Nullable Pairing pairing = code == null ? null : this.pairings.get(code);
    if (pairing == null || pairing.expires() < now || pairing.who() != null) {
      this.failures.put(
          uuid,
          failed == null
              ? new Failures(1, now + APPROVAL_WINDOW.toMillis())
              : new Failures(failed.count() + 1, failed.until()));
      return Approval.UNKNOWN;
    }
    this.pairings.put(
        code,
        new Pairing(pairing.claim(), pairing.expires(), new Session(uuid, name, skin, 0, 0, null)));
    return Approval.APPROVED;
  }

  public synchronized @Nullable Claim claim(
      final @NotNull String claim, final @NotNull Device device) {
    final long now = System.currentTimeMillis();
    final @NotNull String hashed = hash(claim);
    final @Nullable Map.Entry<String, Pairing> entry =
        this.pairings.entrySet().stream()
            .filter(pairing -> pairing.getValue().claim().equals(hashed))
            .findFirst()
            .orElse(null);
    if (entry == null || entry.getValue().expires() < now) {
      return null;
    }
    final @Nullable Session who = entry.getValue().who();
    if (who == null) {
      return new Claim(null);
    }
    this.pairings.remove(entry.getKey());
    return new Claim(login(who, now + SESSION_LIFETIME.toMillis(), device));
  }

  public synchronized @NotNull String open(
      final @NotNull UUID uuid, final @NotNull String name, final @Nullable String skin) {
    final @NotNull String token = token();
    final @NotNull String key = hash(token);
    final long now = System.currentTimeMillis();
    this.sessions.put(
        key, new Session(uuid, name, skin, now + SESSION_LIFETIME.toMillis(), now, null));
    this.seen.put(key, now);
    this.unsaved.add(key);
    return token;
  }

  public synchronized @Nullable Session session(final @Nullable String token) {
    if (token == null) {
      return null;
    }
    final @NotNull String key = hash(token);
    final @Nullable Session session = this.sessions.get(key);
    final long now = System.currentTimeMillis();
    if (session == null || session.expires() < now) {
      return null;
    }
    if (now - this.seen.getOrDefault(key, 0L) >= SEEN_STEP.toMillis()) {
      this.seen.put(key, now);
      if (!this.unsaved.contains(key) && now - this.seenSaved >= SEEN_SAVE.toMillis()) {
        save();
      }
    }
    return session;
  }

  public synchronized @NotNull List<Active> devices(
      final @NotNull UUID uuid, final @Nullable String token) {
    final @Nullable String current = token == null ? null : hash(token);
    final long now = System.currentTimeMillis();
    return this.sessions.entrySet().stream()
        .filter(entry -> entry.getValue().uuid().equals(uuid) && entry.getValue().expires() >= now)
        .map(
            entry ->
                new Active(
                    entry.getKey().substring(0, ID_LENGTH),
                    this.unsaved.contains(entry.getKey()),
                    entry.getValue().device(),
                    entry.getValue().created(),
                    this.seen.getOrDefault(entry.getKey(), entry.getValue().created()),
                    entry.getKey().equals(current)))
        .sorted(
            Comparator.comparing(Active::current)
                .thenComparing(Active::game)
                .thenComparingLong(Active::seen)
                .reversed())
        .toList();
  }

  public synchronized boolean endDevice(final @NotNull UUID uuid, final @NotNull String id) {
    final @Nullable String key =
        id.length() != ID_LENGTH
            ? null
            : this.sessions.entrySet().stream()
                .filter(
                    entry ->
                        entry.getKey().startsWith(id)
                            && entry.getValue().uuid().equals(uuid)
                            && !this.unsaved.contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    if (key == null) {
      return false;
    }
    this.sessions.remove(key);
    save();
    this.onChange.run();
    return true;
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

  public synchronized void revoke(final @NotNull UUID uuid, final @NotNull String node) {
    final @Nullable Set<String> granted = this.grants.get(uuid);
    if (granted != null && granted.contains(node)) {
      grant(
          uuid,
          granted.stream()
              .filter(other -> !other.equals(node))
              .collect(Collectors.toUnmodifiableSet()));
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
    this.codes.values().removeIf(code -> code.who().uuid().equals(uuid));
    this.pairings
        .values()
        .removeIf(pairing -> pairing.who() != null && pairing.who().uuid().equals(uuid));
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
    this.seen.keySet().retainAll(this.sessions.keySet());
    this.seenSaved = now;
    this.grants
        .keySet()
        .removeIf(
            uuid ->
                this.sessions.values().stream().noneMatch(session -> session.uuid().equals(uuid))
                    && this.codes.values().stream()
                        .noneMatch(code -> code.who().uuid().equals(uuid))
                    && this.pairings.values().stream()
                        .noneMatch(
                            pairing -> pairing.who() != null && pairing.who().uuid().equals(uuid)));
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
          yaml.set("sessions." + key + ".created", session.created());
          yaml.set("sessions." + key + ".seen", this.seen.get(key));
          if (session.device() != null) {
            yaml.set("sessions." + key + ".browser", session.device().browser());
            yaml.set("sessions." + key + ".os", session.device().os());
          }
        });
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.logger.log(Level.SEVERE, "Failed to save sessions.yml", e);
    }
  }

  public record Session(
      @NotNull UUID uuid,
      @NotNull String name,
      @Nullable String skin,
      long expires,
      long created,
      @Nullable Device device) {}

  public record Active(
      @NotNull String id,
      boolean game,
      @Nullable Device device,
      long created,
      long seen,
      boolean current) {}

  public record Login(@NotNull String token, @NotNull Session session) {}

  public record Pair(@NotNull String code, @NotNull String claim) {}

  public record Claim(@Nullable Login login) {}

  private record Code(@NotNull Session who, long expires, long until) {}

  private record Pairing(@NotNull String claim, long expires, @Nullable Session who) {}

  private record Failures(int count, long until) {}

  public enum Approval {
    APPROVED,
    UNKNOWN,
    TOO_MANY
  }
}
