package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

public record NavigationTarget(
    String label,
    String world,
    double x,
    @Nullable Double y,
    double z,
    String color,
    @Nullable String player) {}
