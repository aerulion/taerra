package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

public record Device(
    String id,
    boolean game,
    @Nullable String browser,
    @Nullable String os,
    long created,
    long seen,
    boolean current) {}
