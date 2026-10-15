package net.aerulion.taerra.mod.web.model;

public record Account(
    String uuid,
    String name,
    boolean teleport,
    boolean markers,
    boolean privateGroups,
    boolean personal,
    boolean render,
    boolean hide,
    boolean config) {}
