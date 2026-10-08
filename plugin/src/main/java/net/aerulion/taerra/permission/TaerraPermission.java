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
  COMMAND_STATUS(
      "command.status",
      "Gives the user the right to use the '/taerra status' command.",
      PermissionDefault.OP),
  COMMAND_RENDER(
      "command.render",
      "Gives the user the right to use the '/taerra render' command.",
      PermissionDefault.OP),
  COMMAND_WAYPOINT(
      "command.waypoint",
      "Gives the user the right to use the '/taerra waypoint' command.",
      PermissionDefault.OP),
  COMMAND_LIMIT(
      "command.limit",
      "Gives the user the right to limit the area of a world the map renders with '/taerra"
          + " limit'.",
      PermissionDefault.OP),
  COMMAND_CONFIG(
      "command.config",
      "Gives the user the right to change settings and enable worlds with '/taerra config'.",
      PermissionDefault.OP),
  COMMAND_LOGIN(
      "command.login",
      "Gives the user the right to log in to the web map with '/taerra login'.",
      PermissionDefault.TRUE),
  COMMAND_LOGOUT_OTHERS(
      "command.logout.others",
      "Gives the user the right to log other players out of the web map with '/taerra logout"
          + " <player>'.",
      PermissionDefault.OP),
  WEB_SEE_HIDDEN(
      "web.see-hidden",
      "Lets the user see hidden players on the web map, marked as hidden, while logged in.",
      PermissionDefault.OP),
  WEB_TELEPORT(
      "web.teleport",
      "Lets the user teleport themselves by right-clicking the web map while logged in.",
      PermissionDefault.OP),
  WEB_RENDER(
      "web.render",
      "Lets the user render chunks selected on the map again while logged in.",
      PermissionDefault.OP),
  WEB_HIDE(
      "web.hide",
      "Lets the user hide and reveal chunks selected on the map while logged in.",
      PermissionDefault.OP),
  WEB_MARKERS(
      "web.markers",
      "Lets the user add, edit, move and remove markers on the map while logged in.",
      PermissionDefault.OP),
  WEB_MARKERS_PRIVATE(
      "web.markers.private",
      "Lets the user create private marker groups on the map while logged in.",
      PermissionDefault.OP),
  WEB_MARKERS_PERSONAL(
      "web.markers.personal",
      "Lets the user add, edit, move and remove their own markers only they see, while logged in.",
      PermissionDefault.TRUE),
  HIDE("hide", "Hides the player from the web map.", PermissionDefault.FALSE),
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
