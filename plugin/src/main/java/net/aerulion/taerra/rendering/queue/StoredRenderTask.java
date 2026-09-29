package net.aerulion.taerra.rendering.queue;

import org.jetbrains.annotations.Nullable;

record StoredRenderTask(
    String world,
    @Nullable String view,
    @Nullable String rotation,
    int zoom,
    int x,
    int z,
    @Nullable String priority) {}
