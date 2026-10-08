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
    return config.worldEnabled(worldName)
        && (!config.worldPrivate(worldName)
            || this.grants.contains(TaerraConfig.worldPermission(worldName)));
  }

  public boolean canTeleport() {
    return this.grants.contains(TaerraPermission.WEB_TELEPORT.node());
  }

  public boolean canEditMarkers() {
    return this.grants.contains(TaerraPermission.WEB_MARKERS.node());
  }

  public boolean canCreatePrivateGroups() {
    return this.grants.contains(TaerraPermission.WEB_MARKERS_PRIVATE.node());
  }

  public boolean canRender() {
    return this.grants.contains(TaerraPermission.WEB_RENDER.node());
  }

  public boolean canHide() {
    return this.grants.contains(TaerraPermission.WEB_HIDE.node());
  }

  public boolean canSeeHidden() {
    return this.grants.contains(TaerraPermission.WEB_SEE_HIDDEN.node());
  }

  public boolean canSeeLayer(final @NotNull TaerraConfig config, final @NotNull String layerId) {
    return !config.layerPrivate(layerId)
        || this.grants.contains(TaerraConfig.layerPermission(layerId));
  }
}
