package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

public record Account(
    String uuid,
    String name,
    boolean teleport,
    boolean markers,
    boolean privateGroups,
    boolean personal,
    boolean icons,
    boolean renderWorld,
    boolean renderArea,
    boolean cancelRender,
    boolean regions,
    boolean resetRegions,
    boolean config,
    @Nullable String waypointColor) {}
