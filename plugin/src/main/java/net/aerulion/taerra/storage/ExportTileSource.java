package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
interface ExportTileSource {

  @Nullable
  BufferedImage tile(int tileX, int tileZ) throws IOException;
}
