package net.aerulion.taerra.mod.web.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record World(
    String name,
    String displayName,
    String environment,
    @SerializedName("private") boolean restricted,
    Depth depth,
    @Nullable Bounds bounds,
    Spawn spawn,
    @Nullable List<String> views) {

  public boolean shows(final boolean isometric) {
    return this.views == null || this.views.contains(isometric ? "isometric" : "top_down");
  }

  public int minZoom(final @NotNull MapConfig config, final boolean isometric, final int rotation) {
    return isometric
        ? config.isometric().maxZoom() - this.depth.isometric()[rotation]
        : config.maxZoom() - this.depth.topDown();
  }

  public @NotNull Component label() {
    final @NotNull Component name = Component.literal(this.displayName);
    return this.restricted
        ? name.copy()
            .append(Component.translatable("taerra.private.suffix").withStyle(ChatFormatting.GRAY))
        : name;
  }

  public @NotNull Component environmentName() {
    return Component.translatableWithFallback("taerra.env." + this.environment, this.environment);
  }

  public int voidColor() {
    return switch (this.environment) {
      case "NETHER" -> 0xFF170909;
      case "THE_END" -> 0xFF0F0B17;
      default -> 0xFF0B0B0C;
    };
  }

  public record Depth(@SerializedName("top_down") int topDown, int[] isometric) {}

  public record Bounds(int minX, int minZ, int maxX, int maxZ) {}

  public record Spawn(int x, int y, int z) {}
}
