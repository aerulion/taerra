package net.aerulion.taerra.mod.util;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerInfos {

  private PlayerInfos() {}

  public static @Nullable PlayerInfo find(final @NotNull String uuid) {
    final @Nullable ClientPacketListener connection = Minecraft.getInstance().getConnection();
    try {
      return connection == null ? null : connection.getPlayerInfo(UUID.fromString(uuid));
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }
}
