package net.aerulion.taerra.permission;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.function.Predicate;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum TaerraPermission {
  LOGIN(
      "login",
      "Lets the user log in to the web map with '/taerra login', see and log out their devices"
          + " with '/taerra devices' and '/taerra logout', and be signed in by the mod.",
      PermissionDefault.TRUE),
  LOGOUT_OTHERS(
      "logout.others",
      "Lets the user log other players out of the web map with '/taerra logout <player>'.",
      PermissionDefault.OP),
  STATUS(
      "status",
      "Lets the user see what the renderer is doing with '/taerra status'.",
      PermissionDefault.OP),
  MODS(
      "mods",
      "Lets the user see who plays with the TAERRA mod with '/taerra mod'.",
      PermissionDefault.OP),
  DEBUG(
      "debug",
      "Lets the user copy the versions and setup a bug report needs with '/taerra debug'.",
      PermissionDefault.OP),
  TELEPORT(
      "teleport",
      "Lets the user teleport from the web map, the mod's map and their waypoint lists.",
      PermissionDefault.OP),
  RENDER_WORLD(
      "render.world",
      "Lets the user render a whole world again with '/taerra render full' or from the map, and"
          + " get told when a Chunky task left chunks to render.",
      PermissionDefault.OP),
  RENDER_AREA(
      "render.area",
      "Lets the user render an area again with '/taerra render radius' or by selecting chunks on"
          + " the map.",
      PermissionDefault.OP),
  RENDER_CANCEL(
      "render.cancel",
      "Lets the user cancel the running render with '/taerra render cancel' or from the map.",
      PermissionDefault.OP),
  RENDER_FLUSH(
      "render.flush",
      "Lets the user cancel the running render and drop every queued tile with '/taerra render"
          + " flush'.",
      PermissionDefault.OP),
  RENDER_IMAGE(
      "render.image",
      "Lets the user export a world as an image with '/taerra render image'.",
      PermissionDefault.OP),
  LIMIT(
      "limit",
      "Lets the user limit the area of a world the map renders with '/taerra limit'.",
      PermissionDefault.OP),
  CONFIG(
      "config",
      "Lets the user change settings and choose worlds with '/taerra config', on the web map and"
          + " in the mod.",
      PermissionDefault.OP),
  IMPORT(
      "import",
      "Lets the user bring markers over from dynmap with '/taerra import'.",
      PermissionDefault.OP),
  WAYPOINT_EDIT(
      "waypoint.edit",
      "Lets the user add, edit, move and remove the server's waypoints, areas and lines with"
          + " '/taerra waypoint' and on the map.",
      PermissionDefault.OP),
  WAYPOINT_PRIVATE_GROUPS(
      "waypoint.private-groups",
      "Lets the user create private waypoint groups on the map.",
      PermissionDefault.OP),
  WAYPOINT_PERSONAL(
      "waypoint.personal",
      "Lets the user keep their own waypoints, areas and lines only they see on the map.",
      PermissionDefault.TRUE),
  WAYPOINT_DEATH(
      "waypoint.death",
      "Saves where the user last died as one of their personal waypoints.",
      PermissionDefault.TRUE),
  REGION_EDIT(
      "region.edit",
      "Lets the user draw, name, fog and remove regions on the map.",
      PermissionDefault.OP),
  REGION_RESET(
      "region.reset",
      "Lets a user with taerra.region.edit also reset who discovered a region, so it is fogged"
          + " for everyone again.",
      PermissionDefault.OP),
  SEE_HIDDEN_PLAYERS(
      "see.hidden-players",
      "Lets the user see hidden, vanished and anonymous players on the map.",
      PermissionDefault.OP),
  SEE_FOG(
      "see.fog",
      "Lets the user see through the fog of every region on the map, and the players in it.",
      PermissionDefault.OP),
  HIDE("hide", "Hides the player from the map.", PermissionDefault.FALSE),
  ;

  private final @NotNull Permission permission;

  TaerraPermission(
      final @NotNull String name,
      final @NotNull String description,
      final @NotNull PermissionDefault permissionDefault) {
    this.permission = new Permission("taerra." + name, description, permissionDefault);
  }

  public static void registerPermissions(final @NotNull Plugin plugin) {
    Arrays.stream(values())
        .forEach(
            taerraPermission -> {
              if (plugin
                      .getServer()
                      .getPluginManager()
                      .getPermission(taerraPermission.permission.getName())
                  == null) {
                plugin.getServer().getPluginManager().addPermission(taerraPermission.permission);
              }
            });
  }

  public @NotNull String node() {
    return this.permission.getName();
  }

  @Contract("null -> false")
  public boolean check(final @Nullable Permissible permissible) {
    if (permissible == null) {
      return false;
    }
    return permissible.hasPermission(this.permission);
  }

  public @NotNull Predicate<CommandSourceStack> commandRequirement() {
    return commandSourceStack -> commandSourceStack.getSender().hasPermission(this.permission);
  }
}
