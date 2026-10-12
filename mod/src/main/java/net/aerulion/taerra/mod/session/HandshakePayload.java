package net.aerulion.taerra.mod.session;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record HandshakePayload(String json) implements CustomPacketPayload {

  public static final Type<HandshakePayload> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("taerra", "web"));
  public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> buffer.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
          buffer -> {
            final byte @NotNull [] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new HandshakePayload(new String(bytes, StandardCharsets.UTF_8));
          });

  public static @NotNull HandshakePayload version() {
    final @NotNull JsonObject json = new JsonObject();
    json.addProperty(
        "version",
        FabricLoader.getInstance()
            .getModContainer("taerra")
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString());
    return new HandshakePayload(json.toString());
  }

  @Override
  public @NotNull Type<HandshakePayload> type() {
    return TYPE;
  }
}
