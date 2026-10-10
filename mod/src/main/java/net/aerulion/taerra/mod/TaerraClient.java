package net.aerulion.taerra.mod;

import java.util.Objects;
import net.aerulion.taerra.mod.config.Settings;
import net.aerulion.taerra.mod.config.SettingsImpl;
import net.aerulion.taerra.mod.gui.hud.Minimap;
import net.aerulion.taerra.mod.gui.hud.NavigationBeam;
import net.aerulion.taerra.mod.gui.hud.NavigationHud;
import net.aerulion.taerra.mod.gui.hud.WaypointLabels;
import net.aerulion.taerra.mod.gui.screen.MapScreen;
import net.aerulion.taerra.mod.input.TaerraKeys;
import net.aerulion.taerra.mod.map.MapView;
import net.aerulion.taerra.mod.map.MapViewImpl;
import net.aerulion.taerra.mod.navigation.Navigation;
import net.aerulion.taerra.mod.session.HandshakePayload;
import net.aerulion.taerra.mod.session.MapSession;
import net.aerulion.taerra.mod.session.MapSessionImpl;
import net.aerulion.taerra.mod.util.Dimensions;
import net.aerulion.taerra.mod.web.WebMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TaerraClient implements ClientModInitializer {

  public static final int PROTOCOL = 2;

  private static @Nullable Settings settings;
  private static final Navigation NAVIGATION = new Navigation();

  private MapSession session;
  private MapView screenView;

  public static @NotNull Settings settings() {
    return Objects.requireNonNull(settings, "TAERRA is not initialized");
  }

  public static @NotNull Navigation navigation() {
    return NAVIGATION;
  }

  @Override
  public void onInitializeClient() {
    final @NotNull Settings settings =
        SettingsImpl.load(FabricLoader.getInstance().getConfigDir().resolve("taerra.json"));
    TaerraClient.settings = settings;
    this.session = new MapSessionImpl(PROTOCOL, settings);
    this.screenView = new MapViewImpl(settings, 4, false);
    PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
    ClientPlayNetworking.registerGlobalReceiver(
        HandshakePayload.TYPE, (payload, context) -> this.session.connect(payload));
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
    ClientConfigurationConnectionEvents.INIT.register(
        (handler, client) -> client.execute(this::close));
    TaerraKeys.register();
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    final @NotNull Minimap minimap = new Minimap(this.session, settings);
    HudElementRegistry.attachElementBefore(
        VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("taerra", "minimap"), minimap);
    HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, minimap::below);
    LevelRenderEvents.COLLECT_SUBMITS.register(new WaypointLabels(this.session, settings));
    HudElementRegistry.replaceElement(
        VanillaHudElements.BOSS_BAR, new NavigationHud(this.session, NAVIGATION)::above);
    LevelRenderEvents.COLLECT_SUBMITS.register(new NavigationBeam(NAVIGATION));
  }

  private void close() {
    this.session.close();
    NAVIGATION.forget();
  }

  private void tick(final @NotNull Minecraft minecraft) {
    this.session.tick(Dimensions.current());
    NAVIGATION.tick(this.session.map(), minecraft.player, Dimensions.current());
    while (TaerraKeys.STOP_NAVIGATION.consumeClick()) {
      NAVIGATION.stop();
    }
    while (TaerraKeys.WAYPOINT_LIST.consumeClick()) {
      final @Nullable WebMap map = this.session.map();
      if (map != null && map.account() != null && map.account().personal()) {
        final @NotNull MapScreen screen = new MapScreen(map, this.screenView, settings());
        minecraft.setScreenAndShow(screen);
        screen.openWaypointList();
      } else if (minecraft.player != null) {
        minecraft.player.sendOverlayMessage(this.session.unavailable());
      }
    }
    while (TaerraKeys.OPEN_MAP.consumeClick()) {
      final @Nullable WebMap map = this.session.map();
      if (map != null) {
        minecraft.setScreenAndShow(new MapScreen(map, this.screenView, settings()));
      } else if (minecraft.player != null) {
        minecraft.player.sendOverlayMessage(this.session.unavailable());
      }
    }
    final @NotNull Settings settings = settings();
    boolean changed = false;
    while (TaerraKeys.TOGGLE_MINIMAP.consumeClick()) {
      settings.setMinimap(!settings.minimap());
      changed = true;
    }
    while (TaerraKeys.MINIMAP_ZOOM_IN.consumeClick()) {
      settings.setMinimapZoom(
          Math.min(settings.minimapZoom() + 1, Minimap.closestZoom(this.session.map())));
      changed = true;
    }
    while (TaerraKeys.MINIMAP_ZOOM_OUT.consumeClick()) {
      settings.setMinimapZoom(
          Math.max(settings.minimapZoom() - 1, Minimap.farthestZoom(this.session.map())));
      changed = true;
    }
    while (TaerraKeys.TOGGLE_WAYPOINTS.consumeClick()) {
      settings.setWaypointLabels(!settings.waypointLabels());
      changed = true;
    }
    if (changed) {
      settings.save();
    }
  }
}
