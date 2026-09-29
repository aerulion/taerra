package net.aerulion.taerra.rendering.iso;

import org.jetbrains.annotations.NotNull;

public final class IsoProjection {

  public static final int MIN_BLOCK_WIDTH = 4;

  private final int blockWidth;
  private final int halfWidth;
  private final int quarterWidth;

  private IsoProjection(final int blockWidth) {
    super();
    this.blockWidth = blockWidth;
    this.halfWidth = blockWidth / 2;
    this.quarterWidth = blockWidth / 4;
  }

  public static @NotNull IsoProjection ofBlockWidth(final int blockWidth) {
    if (blockWidth < MIN_BLOCK_WIDTH || blockWidth % 4 != 0) {
      throw new IllegalArgumentException(
          "Block width must be at least "
              + MIN_BLOCK_WIDTH
              + " and a multiple of 4, got "
              + blockWidth);
    }
    return new IsoProjection(blockWidth);
  }

  public int blockWidth() {
    return this.blockWidth;
  }

  public int halfWidth() {
    return this.halfWidth;
  }

  public int quarterWidth() {
    return this.quarterWidth;
  }

  public int blockScreenHeight() {
    return this.blockWidth;
  }

  public long screenX(final int blockX, final int blockZ) {
    return (long) (blockX - blockZ) * this.halfWidth;
  }

  public long screenY(final int blockX, final int blockY, final int blockZ) {
    return (long) (blockX + blockZ) * this.quarterWidth - (long) blockY * this.halfWidth;
  }

  public int faceInset(final int columnOffset) {
    return columnOffset >= 0 ? (columnOffset + 1) / 2 : -columnOffset / 2;
  }

  public int topFaceFirstRow(final int columnOffset) {
    return faceInset(columnOffset);
  }

  public int topFaceLastRow(final int columnOffset) {
    return this.halfWidth - 1 - faceInset(columnOffset);
  }

  public int sideFaceFirstRow(final int columnOffset) {
    return this.halfWidth - faceInset(columnOffset);
  }

  public boolean isPositiveXFace(final int columnOffset) {
    return columnOffset >= 0;
  }

  public int firstColumnOffset() {
    return -this.halfWidth;
  }

  public int lastColumnOffset() {
    return this.halfWidth - 1;
  }
}
