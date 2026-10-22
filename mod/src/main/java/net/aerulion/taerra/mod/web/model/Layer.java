package net.aerulion.taerra.mod.web.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Layer(
    String id,
    String label,
    boolean shown,
    @SerializedName("private") boolean restricted,
    boolean personal,
    List<Marker> markers,
    @Nullable String icon) {

  public static final String PERSONAL = "personal";
  public static final Layer PLAYERS =
      new Layer("players", "Players", true, false, false, List.of(), null);

  public @NotNull Component displayName() {
    return Component.translatableWithFallback("taerra.layer." + this.id, this.label);
  }

  public boolean waypoints() {
    return this.personal
        || this.id.equals("waypoints")
        || this.id.equals("spawn")
        || this.id.startsWith("waypoints.");
  }
}
