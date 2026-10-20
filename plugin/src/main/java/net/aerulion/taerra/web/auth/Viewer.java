package net.aerulion.taerra.web.auth;

import java.util.Set;
import java.util.UUID;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.permission.TaerraPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Viewer(@Nullable UUID uuid, @NotNull Set<String> grants) {

  public static final @NotNull Viewer ANONYMOUS = new Viewer(null, Set.of());

  public boolean canSee(final @NotNull TaerraConfig config, final @NotNull String worldName) {
    return config.worldVisible(worldName, this.grants::contains);
  }

  public boolean usesMod() {
    return this.grants.contains(WebGrants.MOD);
  }

  public boolean canTeleport() {
    return has(TaerraPermission.TELEPORT);
  }

  public boolean canEditMarkers() {
    return has(TaerraPermission.WAYPOINT_EDIT);
  }

  public boolean canCreatePrivateGroups() {
    return has(TaerraPermission.WAYPOINT_PRIVATE_GROUPS);
  }

  public boolean canKeepPersonalMarkers() {
    return this.uuid != null && has(TaerraPermission.WAYPOINT_PERSONAL);
  }

  public boolean canRenderWorld() {
    return has(TaerraPermission.RENDER_WORLD);
  }

  public boolean canRenderArea() {
    return has(TaerraPermission.RENDER_AREA);
  }

  public boolean canCancelRender() {
    return has(TaerraPermission.RENDER_CANCEL);
  }

  public boolean canEditRegions() {
    return has(TaerraPermission.REGION_EDIT);
  }

  public boolean canResetRegions() {
    return has(TaerraPermission.REGION_RESET);
  }

  public boolean canConfigure() {
    return this.uuid != null && has(TaerraPermission.CONFIG);
  }

  public boolean canSeeHiddenPlayers() {
    return has(TaerraPermission.SEE_HIDDEN_PLAYERS);
  }

  public boolean canSeeThroughFog() {
    return has(TaerraPermission.SEE_FOG);
  }

  public boolean canSeePlayers(
      final @NotNull TaerraConfig config, final @NotNull String worldName) {
    return !config.playersPrivate(worldName)
        || this.grants.contains(TaerraConfig.playersPermission(worldName));
  }

  public boolean canSeeLayer(final @NotNull TaerraConfig config, final @NotNull String layerId) {
    return !config.layerPrivate(layerId)
        || this.grants.contains(TaerraConfig.layerPermission(layerId));
  }

  private boolean has(final @NotNull TaerraPermission permission) {
    return this.grants.contains(permission.node());
  }
}
