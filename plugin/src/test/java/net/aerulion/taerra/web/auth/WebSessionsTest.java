package net.aerulion.taerra.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    final WebSessions.@Nullable Login login = sessions.redeem(code);
    assertNotNull(login);
    assertNull(sessions.redeem(code));
    assertNull(sessions.redeem("guessed"));
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
  @DisplayName("a pairing code waits until a player approves it, then logs in its browser once")
  void pairingLogsInOnce() {
    final @NotNull WebSessions sessions = sessions();
    final WebSessions.@Nullable Pair pair = sessions.pair();
    assertNotNull(pair);

    final WebSessions.@Nullable Claim waiting = sessions.claim(pair.claim());
    assertNotNull(waiting);
    assertNull(waiting.login());
    assertNull(sessions.claim("guessed"));

    final @Nullable String typed =
        WebSessions.pairCode(
            " " + pair.code().substring(0, 3).toLowerCase() + "-" + pair.code().substring(3));
    assertEquals(pair.code(), typed);
    assertTrue(sessions.approve(pair.code(), PLAYER, "Steve", SKIN));
    assertFalse(sessions.approve(pair.code(), UUID.randomUUID(), "Alex", null));

    final WebSessions.@Nullable Claim claimed = sessions.claim(pair.claim());
    assertNotNull(claimed);
    assertNotNull(claimed.login());
    assertEquals(PLAYER, claimed.login().session().uuid());
    assertNull(sessions.claim(pair.claim()));
    assertFalse(sessions.approve(pair.code(), PLAYER, "Steve", SKIN));
  }

  @Test
  @DisplayName("pairing codes are six letters and digits that can't be misread")
  void pairCodeFormat() {
    assertEquals("K7F2QX", WebSessions.pairCode("k7f 2qx"));
    assertNull(WebSessions.pairCode("K7F2Q"));
    assertNull(WebSessions.pairCode("K7F2Q0"));
    assertNull(WebSessions.pairCode("K7F2QXX"));
  }

  @Test
  @DisplayName(
      "a link made from a browser session ends with that session, and never from a mod session")
  void linkEndsWithItsSession() {
    final @NotNull WebSessions sessions = sessions();
    final WebSessions.@Nullable Login desktop =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN));
    assertNotNull(desktop);
    final @Nullable String code = sessions.linkCode(desktop.token());
    assertNotNull(code);
    final WebSessions.@Nullable Login phone = sessions.redeem(code);
    assertNotNull(phone);
    assertTrue(phone.session().expires() <= desktop.session().expires());

    assertNull(sessions.linkCode(sessions.open(PLAYER, "Steve", SKIN)));
    assertNull(sessions.linkCode("guessed"));
    assertNull(sessions.linkCode(null));
  }

  @Test
  @DisplayName("a mod session works but never reaches the disk")
  void modSessionStaysInMemory() {
    final @NotNull WebSessions sessions = sessions();
    final @NotNull String token = sessions.open(PLAYER, "Steve", SKIN);
    assertNotNull(sessions.session(token));
    assertFalse(Files.exists(this.folder.resolve("sessions.yml")), "opening writes nothing");

    final WebSessions.@Nullable Login saved =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN));
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
    final WebSessions.@Nullable Login first =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN));
    final WebSessions.@Nullable Login second =
        sessions.redeem(sessions.createCode(PLAYER, "Steve", SKIN));
    final WebSessions.@Nullable Login other =
        sessions.redeem(sessions.createCode(UUID.randomUUID(), "Alex", null));
    assertNotNull(first);
    assertNotNull(second);
    assertNotNull(other);

    sessions.end(first.token());
    sessions.end("unknown");
    assertEquals(1, ends.get(), "only an ended session notifies");
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
    sessions.grant(PLAYER, Set.of("taerra.web.world.minecraft.the_end"));
    sessions.grant(PLAYER, Set.of("taerra.web.world.minecraft.the_end"));
    assertEquals(1, changes.get());

    final WebSessions.@Nullable Login login = sessions.redeem(code);
    assertNotNull(login);
    assertEquals(Viewer.ANONYMOUS, sessions.viewer(null));
    assertEquals(Viewer.ANONYMOUS, sessions.viewer("guessed"));

    final @NotNull WebSessions restarted = sessions();
    restarted.load();
    assertEquals(
        new Viewer(PLAYER, Set.of("taerra.web.world.minecraft.the_end")),
        restarted.viewer(login.token()));

    restarted.endAll(PLAYER);
    restarted.grant(UUID.randomUUID(), Set.of("taerra.web.layer.worldguard"));
    assertEquals(Set.of(), restarted.accounts());
    assertFalse(
        Files.readString(this.folder.resolve("sessions.yml")).contains("taerra.web"),
        "grants of players without a session are dropped");
  }

  @Test
  @DisplayName("a private world or layer shows only to viewers holding its permission")
  void privateWorldsAndLayers() {
    final @NotNull TaerraConfig config =
        new TaerraConfig(
            1,
            0,
            0,
            1,
            256,
            new CopyOnWriteArrayList<>(List.of("minecraft:overworld", "minecraft:the_end")),
            new CopyOnWriteArrayList<>(List.of("minecraft:the_end")),
            new CopyOnWriteArrayList<>(List.of("worldguard")),
            true,
            false,
            true,
            true);
    final @NotNull Viewer staff =
        new Viewer(
            PLAYER, Set.of("taerra.web.world.minecraft.the_end", "taerra.web.layer.worldguard"));

    assertTrue(Viewer.ANONYMOUS.canSee(config, "minecraft:overworld"));
    assertFalse(Viewer.ANONYMOUS.canSee(config, "minecraft:the_end"));
    assertTrue(staff.canSee(config, "minecraft:the_end"));
    assertFalse(staff.canSee(config, "minecraft:the_nether"), "disabled worlds stay hidden");

    assertTrue(Viewer.ANONYMOUS.canSeeLayer(config, "waypoints"));
    assertFalse(Viewer.ANONYMOUS.canSeeLayer(config, "worldguard"));
    assertTrue(staff.canSeeLayer(config, "worldguard"));
    assertEquals(
        Set.of("taerra.web.world.minecraft.the_end", "taerra.web.layer.worldguard"),
        config.webPermissions());
  }
}
