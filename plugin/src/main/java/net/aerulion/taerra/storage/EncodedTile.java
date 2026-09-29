package net.aerulion.taerra.storage;

import org.jetbrains.annotations.NotNull;

public record EncodedTile(byte[] data, String eTag) {

  public static @NotNull String eTagOf(final byte @NotNull [] data) {
    long hash = 0xcbf29ce484222325L;
    for (final byte value : data) {
      hash ^= value & 0xFF;
      hash *= 0x100000001b3L;
    }
    return "\"" + Integer.toHexString(data.length) + '-' + Long.toHexString(hash) + '"';
  }
}
