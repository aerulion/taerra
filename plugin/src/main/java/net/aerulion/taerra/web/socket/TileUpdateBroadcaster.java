package net.aerulion.taerra.web.socket;

import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public sealed interface TileUpdateBroadcaster permits TileUpdateBroadcasterImpl {

  void tileUpdated(@NotNull TileCoordinate coord);

  void start();

  void stop();
}
