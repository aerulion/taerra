package net.aerulion.taerra.mod.map;

import java.util.List;
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
    @Nullable String hidden) {}
