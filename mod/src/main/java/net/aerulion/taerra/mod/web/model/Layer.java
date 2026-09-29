package net.aerulion.taerra.mod.web.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public record Layer(
    String id,
    String label,
    boolean shown,
    @SerializedName("private") boolean restricted,
    List<Marker> markers) {

  public @NotNull Component displayName() {
    return Component.translatableWithFallback("taerra.layer." + this.id, this.label);
  }

  public boolean waypoints() {
    return this.id.equals("waypoints") || this.id.startsWith("waypoints.");
  }
}
