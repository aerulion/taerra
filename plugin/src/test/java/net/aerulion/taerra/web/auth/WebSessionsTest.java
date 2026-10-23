package net.aerulion.taerra.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.aerulion.taerra.config.TaerraConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebSessionsTest {

  private static final @NotNull UUID PLAYER = UUID.randomUUID();
  private static final @NotNull String SKIN = "https://textures.minecraft.net/texture/abc";
  private static final @NotNull Device PHONE = new Device("Safari", "iPhone");

  @TempDir Path folder;

  private @NotNull WebSessions sessions() {
    return new WebSessions(
        this.folder.resolve("sessions.yml").toFile(), Logger.getLogger("WebSessionsTest"));
  }

  @Test
  @DisplayName("a code logs in once, and the session survives a restart")
  void codeRedeemsOnce() throws Exception {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String code = sessions.createCode(PLAYER, "Steve", SKIN);

    final WebSessions.@Nullable Login login = sessions.redeem(code, PHONE);
    assertNotNull(login);
    assertNull(sessions.redeem(code, PHONE));
    assertNull(sessions.redeem("guessed", PHONE));
    assertNull(sessions.session(code));

    final @NotNull WebSessions restarted = sessions();
    restarted.load();
    final WebSessions.@Nullable Session session = restarted.session(login.token());
    assertNotNull(session);
    assertEquals(PLAYER, session.uuid());
    assertEquals("Steve", session.name());
    assertEquals(SKIN, session.skin());

    final @NotNull String saved = Files.readString(this.folder.resolve("sessions.yml"));
    assertFalse(saved.contains(login.token()), "only hashes are written to disk");
  }

  @Test
  @DisplayName("a flood of pairing codes pushes out the oldest instead of refusing new ones")
  void pairingFloodDropsOldest() {
    final @NotNull WebSessions sessions = sessions();
    final WebSessions.@NotNull Pair first = sessions.pair();
    for (int i = 0; i < WebSessions.MAX_PAIRINGS; i++) {
      sessions.pair();
    }
    assertNull(sessions.claim(first.claim(), PHONE));
    assertNotNull(sessions.claim(sessions.pair().claim(), PHONE));
  }

  @Test
  @DisplayName("a pairing code waits until a player approves it, then logs in its browser once")
  void pairingLogsInOnce() {
    final @NotNull WebSessions sessions = sessions();
    final WebSessions.@Nullable Pair pair = sessions.pair();
    assertNotNull(pair);

    final WebSessions.@Nullable Claim waiting = sessions.claim(pair.claim(), PHONE);
    assertNotNull(waiting);
    assertNull(waiting.login());
    assertNull(sessions.claim("guessed", PHONE));

    final @Nullable String typed =
        WebSessions.pairCode(
            " " + pair.code().substring(0, 3).toLowerCase() + "-" + pair.code().substring(3));
    assertEquals(pair.code(), typed);
    assertEquals(
        WebSessions.Approval.APPROVED, sessions.approve(pair.code(), PLAYER, "Steve", SKIN));
    assertEquals(
        WebSessions.Approval.UNKNOWN,
        sessions.approve(pair.code(), UUID.randomUUID(), "Alex", null));

    final WebSessions.@Nullable Claim claimed = sessions.claim(pair.claim(), PHONE);
    assertNotNull(claimed);
    assertNotNull(claimed.login());
    assertEquals(PLAYER, claimed.login().session().uuid());
    assertNull(sessions.claim(pair.claim(), PHONE));
    assertEquals(
        WebSessions.Approval.UNKNOWN, sessions.approve(pair.code(), PLAYER, "Steve", SKIN));
  }

  @Test
  @DisplayName("a player who keeps typing wrong codes has to wait")
  void wrongCodesAreThrottled() {
    final @NotNull WebSessions sessions = sessions();
    final WebSessions.@Nullable Pair pair = sessions.pair();
    assertNotNull(pair);
    for (int i = 0; i < 5; i++) {
      assertEquals(WebSessions.Approval.UNKNOWN, sessions.approve("AAAAAA", PLAYER, "Steve", SKIN));
    }
    assertEquals(
        WebSessions.Approval.TOO_MANY, sessions.approve(pair.code(), PLAYER, "Steve", SKIN));
    assertEquals(
        WebSessions.Approval.APPROVED,
        sessions.approve(pair.code(), UUID.randomUUID(), "Alex", null));
  }

  @Test
  @DisplayName("logging a player out also cancels their unused links and approved codes")
  void logoutCancelsPendingLogins() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String link = sessions.createCode(PLAYER, "Steve", SKIN);
    final WebSessions.@Nullable Pair pair = sessions.pair();
    assertNotNull(pair);
    sessions.approve(pair.code(), PLAYER, "Steve", SKIN);
    assertNotNull(sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE));

    sessions.endAll(PLAYER);
    assertNull(sessions.redeem(link, PHONE));
    assertNull(sessions.claim(pair.claim(), PHONE));
  }

  @Test
  @DisplayName("a new link replaces the player's previous one")
  void newLinkReplacesOld() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String first = sessions.createCode(PLAYER, "Steve", SKIN);
    final @NotNull String second = sessions.createCode(PLAYER, "Steve", SKIN);
    assertNull(sessions.redeem(first, PHONE));
    assertNotNull(sessions.redeem(second, PHONE));
  }

  @Test
  @DisplayName("a player keeps at most ten browser sessions, never losing the newest or the mod's")
  void sessionsPerPlayerAreCapped() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String mod = sessions.open(PLAYER, "Steve", SKIN);
    final @NotNull List<String> tokens = new java.util.ArrayList<>();
    WebSessions.@Nullable Login last =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE);
    for (int i = 0; i < 12; i++) {
      assertNotNull(last);
      tokens.add(last.token());
      final @Nullable String code = sessions.linkCode(last.token());
      assertNotNull(code);
      last = sessions.redeem(code, PHONE);
    }
    assertNotNull(last);
    assertNotNull(sessions.session(last.token()));
    assertNotNull(sessions.session(mod));
    assertEquals(9, tokens.stream().filter(token -> sessions.session(token) != null).count());
  }

  @Test
  @DisplayName("a player sees their own devices and can log out a browser, but not the game")
  void devices() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull Device firefox = new Device("Firefox", "Windows");
    final WebSessions.@Nullable Login phone =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE);
    final WebSessions.@Nullable Login desktop =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), firefox);
    final @NotNull String game = sessions.open(PLAYER, "Steve", SKIN);
    sessions.redeem(sessions.createCode(UUID.randomUUID(), "Alex", null), PHONE);
    assertNotNull(phone);
    assertNotNull(desktop);

    final @NotNull List<WebSessions.Active> devices = sessions.devices(PLAYER, desktop.token());
    assertEquals(3, devices.size());
    assertTrue(devices.get(0).current());
    assertEquals(firefox, devices.get(0).device());
    assertTrue(devices.get(1).game());
    assertEquals(PHONE, devices.get(2).device());

    assertFalse(sessions.endDevice(UUID.randomUUID(), devices.get(2).id()));
    assertFalse(sessions.endDevice(PLAYER, devices.get(1).id()));
    assertFalse(sessions.endDevice(PLAYER, devices.get(2).id().substring(1)));
    assertTrue(sessions.endDevice(PLAYER, devices.get(2).id()));
    assertNull(sessions.session(phone.token()));
    assertNotNull(sessions.session(game));

    final @NotNull WebSessions restarted = sessions();
    restarted.load();
    final @NotNull List<WebSessions.Active> saved = restarted.devices(PLAYER, desktop.token());
    assertEquals(1, saved.size());
    assertEquals(firefox, saved.getFirst().device());
  }

  @Test
  @DisplayName("a mod session works but never reaches the disk")
  void modSessionStaysInMemory() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String token = sessions.open(PLAYER, "Steve", SKIN);
    assertNotNull(sessions.session(token));
    assertFalse(Files.exists(this.folder.resolve("sessions.yml")), "opening writes nothing");

    final WebSessions.@Nullable Login saved =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE);
    assertNotNull(saved);
    final @NotNull WebSessions restarted = sessions();
    restarted.load();
    assertNull(restarted.session(token));
    assertNotNull(restarted.session(saved.token()));

    sessions.end(token);
    assertNull(sessions.session(token));
  }

  @Test
  @DisplayName("logging out ends the session, logging out everywhere ends them all")
  void logout() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull AtomicInteger ends = new AtomicInteger();
    sessions.onChange(ends::incrementAndGet);
    final @NotNull List<UUID> devices = new ArrayList<>();
    sessions.onDevices(devices::add);
    final WebSessions.@Nullable Login first =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE);
    final WebSessions.@Nullable Login second =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN), PHONE);
    final WebSessions.@Nullable Login other =
        sessions.redeem(sessions.createCode(UUID.randomUUID(), "Alex", null), PHONE);
    assertNotNull(first);
    assertNotNull(second);
    assertNotNull(other);

    sessions.end(first.token());
    sessions.end("unknown");
    assertEquals(1, ends.get(), "only an ended session notifies");
    assertEquals(
        List.of(PLAYER, PLAYER, devices.get(2), PLAYER),
        devices,
        "logins and ends tell the account's devices");
    assertNotEquals(PLAYER, devices.get(2));
    assertNull(sessions.session(first.token()));
    assertNotNull(sessions.session(second.token()));

    assertEquals(1, sessions.endAll(PLAYER));
    assertEquals(2, ends.get());
    assertNull(sessions.session(second.token()));
    assertNotNull(sessions.session(other.token()));
  }

  @Test
  @DisplayName("grants follow the session, survive a restart, and only notify when they change")
  void grants() throws Exception {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull AtomicInteger changes = new AtomicInteger();
    sessions.onChange(changes::incrementAndGet);
    final @NotNull String code = sessions.createCode(PLAYER, "Steve", SKIN);
    sessions.grant(PLAYER, Set.of("taerra.world.minecraft.the_end"));
    sessions.grant(PLAYER, Set.of("taerra.world.minecraft.the_end"));
    assertEquals(1, changes.get());
    sessions.grant(
        Map.of(
            PLAYER,
            Set.of("taerra.world.minecraft.the_end"),
            UUID.randomUUID(),
            Set.of("taerra.teleport"),
            UUID.randomUUID(),
            Set.of("taerra.status")));
    assertEquals(2, changes.get(), "a batch notifies once");

    final WebSessions.@Nullable Login login = sessions.redeem(code, PHONE);
    assertNotNull(login);
    assertEquals(Viewer.ANONYMOUS, sessions.viewer(null));
    assertEquals(Viewer.ANONYMOUS, sessions.viewer("guessed"));

    final @NotNull WebSessions restarted = sessions();
    restarted.load();
    assertEquals(
        new Viewer(PLAYER, Set.of("taerra.world.minecraft.the_end")),
        restarted.viewer(login.token()));

    restarted.endAll(PLAYER);
    restarted.grant(UUID.randomUUID(), Set.of("taerra.layer.worldguard"));
    assertEquals(Set.of(), restarted.accounts());
    assertFalse(
        Files.readString(this.folder.resolve("sessions.yml")).contains("taerra.web"),
        "grants of players without a session are dropped");
  }

  @Test
  @DisplayName(
      "a private world, layer or world's players show only to viewers holding its permission")
  void privateWorldsAndLayers() {
    final @NotNull TaerraConfig config =
        new TaerraConfig(
            1,
            0,
            1,
            256,
            new CopyOnWriteArrayList<>(List.of("minecraft:overworld", "minecraft:the_end")),
            new CopyOnWriteArrayList<>(List.of("minecraft:the_end")),
            new CopyOnWriteArrayList<>(List.of("minecraft:overworld")),
            new CopyOnWriteArrayList<>(List.of("worldguard")),
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
    final @NotNull Viewer staff =
        new Viewer(
            PLAYER,
            Set.of(
                "taerra.world.minecraft.the_end",
                "taerra.players.minecraft.overworld",
                "taerra.layer.worldguard"));

    assertTrue(Viewer.ANONYMOUS.canSee(config, "minecraft:overworld"));
    assertFalse(Viewer.ANONYMOUS.canSee(config, "minecraft:the_end"));
    assertTrue(staff.canSee(config, "minecraft:the_end"));
    assertFalse(staff.canSee(config, "minecraft:the_nether"), "disabled worlds stay hidden");

    assertTrue(Viewer.ANONYMOUS.canSeeLayer(config, "waypoints"));
    assertFalse(Viewer.ANONYMOUS.canSeeLayer(config, "worldguard"));
    assertTrue(staff.canSeeLayer(config, "worldguard"));
    assertFalse(Viewer.ANONYMOUS.canSeePlayers(config, "minecraft:overworld"));
    assertTrue(Viewer.ANONYMOUS.canSeePlayers(config, "minecraft:the_end"));
    assertTrue(staff.canSeePlayers(config, "minecraft:overworld"));
    assertEquals(
        Set.of(
            "taerra.world.minecraft.the_end",
            "taerra.players.minecraft.overworld",
            "taerra.layer.worldguard"),
        config.webPermissions());
  }
}
