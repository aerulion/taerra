package net.aerulion.taerra.web.player;

import java.util.HashMap;
import java.util.Map;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.permission.TaerraPermission;
import net.aerulion.taerra.web.auth.ModHandshake;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class PlayerMapPayloadSerializer {

  private PlayerMapPayloadSerializer() {
    super();
  }

  @SuppressWarnings("deprecation")
  public static boolean shouldIncludePlayer(final @NotNull Player player) {
    return !player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        && player.getMetadata("vanished").stream().noneMatch(MetadataValue::asBoolean)
        && !TaerraPermission.HIDE.check(player);
  }

  public static boolean isSpectator(final @NotNull Player player) {
    return player.getGameMode() == GameMode.SPECTATOR;
  }

  public static @NotNull Map<String, Object> serialize(final @NotNull Player player) {
    final @NotNull Map<String, Object> data = new HashMap<>();
    data.put("name", player.getName());
    data.put("uuid", player.getUniqueId().toString());
    data.put("world", WorldKeys.id(player.getWorld()));

    final @NotNull Location loc = player.getLocation();
    data.put("x", loc.getBlockX());
    data.put("y", loc.getBlockY());
    data.put("z", loc.getBlockZ());
    data.put("yaw", loc.getYaw());
    data.put("color", String.format("#%06x", locatorBarColor(player)));
    data.put("mod", ModHandshake.uses(player));
    return data;
  }

  private static int locatorBarColor(final @NotNull Player player) {
    final @NotNull ServerPlayer handle = ((CraftPlayer) player).getHandle();
    return handle
            .waypointIcon()
            .cloneAndAssignStyle(handle)
            .color
            .orElseGet(
                () -> ARGB.setBrightness(ARGB.color(255, player.getUniqueId().hashCode()), 0.9F))
        & 0xFFFFFF;
  }
}
