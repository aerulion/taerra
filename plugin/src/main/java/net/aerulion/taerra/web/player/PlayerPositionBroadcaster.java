package net.aerulion.taerra.web.player;

import java.util.UUID;
import net.aerulion.taerra.web.auth.Viewer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface PlayerPositionBroadcaster permits PlayerPositionBroadcasterImpl {

  void start();

  void stop();

  @NotNull
  String players(@NotNull Viewer viewer);

  @Nullable
  Player listed(@NotNull UUID uuid, @NotNull Viewer viewer);
}
