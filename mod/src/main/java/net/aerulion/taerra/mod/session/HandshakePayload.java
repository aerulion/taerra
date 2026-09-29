package net.aerulion.taerra.mod.session;

import java.nio.charset.StandardCharsets;
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

  @Override
  public @NotNull Type<HandshakePayload> type() {
    return TYPE;
  }
}
