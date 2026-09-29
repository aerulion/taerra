package net.aerulion.taerra.mod.session;

import net.aerulion.taerra.mod.web.WebMap;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public sealed interface MapSession permits MapSessionImpl {

  @Nullable
  WebMap map();

  Component unavailable();

  void connect(HandshakePayload handshake);

  void close();

  void tick(String playerWorld);
}
