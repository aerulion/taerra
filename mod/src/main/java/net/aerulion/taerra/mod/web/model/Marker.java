package net.aerulion.taerra.mod.web.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record Marker(
    String id,
    String label,
    String color,
    @Nullable String description,
    String type,
    @Nullable List<List<String>> details,
    @Nullable List<Double> position,
    @Nullable List<List<Double>> points,
    double minY,
    double maxY,
    boolean hollow,
    boolean fullHeight,
    boolean smooth,
    boolean muted,
    @Nullable Long discovered,
    @Nullable String icon) {}
