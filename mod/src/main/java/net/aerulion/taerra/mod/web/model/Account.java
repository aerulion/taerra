package net.aerulion.taerra.mod.web.model;

public record Account(
    String uuid,
    String name,
    boolean teleport,
    boolean markers,
    boolean privateGroups,
    boolean personal,
    boolean renderWorld,
    boolean renderArea,
    boolean cancelRender,
    boolean regions,
    boolean resetRegions,
    boolean config) {}
