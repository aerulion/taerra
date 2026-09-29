package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MapRotation {
  R0(0),
  R90(1),
  R180(2),
  R270(3);

  public static final int COUNT = 4;
  private static final @NotNull MapRotation @NotNull [] VALUES = values();
  private final int quarterTurns;

  MapRotation(final int quarterTurns) {
    this.quarterTurns = quarterTurns;
  }

  public static @Nullable MapRotation ofIndex(final int index) {
    return index >= 0 && index < VALUES.length ? VALUES[index] : null;
  }

  public static @NotNull MapRotation fromIndex(
      final int index, final @NotNull MapRotation fallback) {
    return index >= 0 && index < VALUES.length ? VALUES[index] : fallback;
  }

  public static @NotNull MapRotation fromId(
      final @Nullable String id, final @NotNull MapRotation fallback) {
    if (id == null) {
      return fallback;
    }
    try {
      return fromIndex(Integer.parseInt(id), fallback);
    } catch (final NumberFormatException e) {
      return fallback;
    }
  }

  public int index() {
    return this.quarterTurns;
  }

  public @NotNull String id() {
    return Integer.toString(this.quarterTurns);
  }

  public long rotatedX(final long x, final long z) {
    return switch (this.quarterTurns) {
      case 1 -> z;
      case 2 -> -x - 1L;
      case 3 -> -z - 1L;
      default -> x;
    };
  }

  public long rotatedZ(final long x, final long z) {
    return switch (this.quarterTurns) {
      case 1 -> -x - 1L;
      case 2 -> -z - 1L;
      case 3 -> x;
      default -> z;
    };
  }

  public int rotatedX(final int x, final int z) {
    return switch (this.quarterTurns) {
      case 1 -> z;
      case 2 -> -x - 1;
      case 3 -> -z - 1;
      default -> x;
    };
  }

  public int rotatedZ(final int x, final int z) {
    return switch (this.quarterTurns) {
      case 1 -> -x - 1;
      case 2 -> -z - 1;
      case 3 -> x;
      default -> z;
    };
  }

  public int worldX(final int rotatedX, final int rotatedZ) {
    return switch (this.quarterTurns) {
      case 1 -> -rotatedZ - 1;
      case 2 -> -rotatedX - 1;
      case 3 -> rotatedZ;
      default -> rotatedX;
    };
  }

  public int worldZ(final int rotatedX, final int rotatedZ) {
    return switch (this.quarterTurns) {
      case 1 -> rotatedX;
      case 2 -> -rotatedZ - 1;
      case 3 -> -rotatedX - 1;
      default -> rotatedZ;
    };
  }

  public int worldDeltaX(final int rotatedDeltaX, final int rotatedDeltaZ) {
    return switch (this.quarterTurns) {
      case 1 -> -rotatedDeltaZ;
      case 2 -> -rotatedDeltaX;
      case 3 -> rotatedDeltaZ;
      default -> rotatedDeltaX;
    };
  }

  public int worldDeltaZ(final int rotatedDeltaX, final int rotatedDeltaZ) {
    return switch (this.quarterTurns) {
      case 1 -> rotatedDeltaX;
      case 2 -> -rotatedDeltaZ;
      case 3 -> -rotatedDeltaX;
      default -> rotatedDeltaZ;
    };
  }

  public int localX(final int localRotatedX, final int localRotatedZ) {
    return switch (this.quarterTurns) {
      case 1 -> 15 - localRotatedZ;
      case 2 -> 15 - localRotatedX;
      case 3 -> localRotatedZ;
      default -> localRotatedX;
    };
  }

  public int localZ(final int localRotatedX, final int localRotatedZ) {
    return switch (this.quarterTurns) {
      case 1 -> localRotatedX;
      case 2 -> 15 - localRotatedZ;
      case 3 -> 15 - localRotatedX;
      default -> localRotatedZ;
    };
  }
}
