package net.aerulion.taerra.mod.map;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Drawing(
    @NotNull ChunkSelection chunks,
    double @Nullable [][] stroke,
    boolean erasing,
    @NotNull List<double[]> points,
    boolean closed,
    boolean smooth,
    @Nullable Integer color,
    @Nullable String hidden,
    @NotNull List<Long> hover,
    boolean hoverErase,
    @NotNull List<Area> others) {

  public record Area(@NotNull Set<Long> chunks, @NotNull List<int[][]> rings, int color) {}
}
