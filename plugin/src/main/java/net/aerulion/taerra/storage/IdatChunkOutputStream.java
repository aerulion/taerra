package net.aerulion.taerra.storage;

import java.io.IOException;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

final class IdatChunkOutputStream extends OutputStream {

  private final @NotNull OutputStream file;

  IdatChunkOutputStream(final @NotNull OutputStream file) {
    super();
    this.file = file;
  }

  @Override
  public void write(final int b) throws IOException {
    write(new byte[] {(byte) b}, 0, 1);
  }

  @Override
  public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
    if (len > 0) {
      WorldImageExporter.writeChunk(this.file, WorldImageExporter.IDAT, b, off, len);
    }
  }
}
