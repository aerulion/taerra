package net.aerulion.taerra.api.event;

import org.bukkit.event.Event;
import org.jetbrains.annotations.ApiStatus;

/** An event fired by TAERRA. Only TAERRA creates its events. */
@ApiStatus.NonExtendable
public abstract class TaerraEvent extends Event {

  /** Creates an event fired on the server thread. */
  @ApiStatus.Internal
  protected TaerraEvent() {
    super();
  }
}
