package net.aerulion.taerra.mod.web.model;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MapIcon(
    String id,
    @Nullable String label,
    String source,
    String version,
    boolean tint,
    @Nullable Integer uses) {

  public @NotNull String path() {
    return "/api/icons/" + this.id.replace(':', '/');
  }

  public boolean uploaded() {
    return "upload".equals(this.source);
  }

  public @NotNull Component displayName() {
    if (this.label == null) {
      return Component.translatable("taerra.icon." + this.id.substring(this.id.indexOf(':') + 1));
    }
    return "plugin".equals(this.source)
        ? Component.literal(this.label + " (" + this.id.substring(0, this.id.indexOf(':')) + ")")
        : Component.literal(this.label);
  }
}
