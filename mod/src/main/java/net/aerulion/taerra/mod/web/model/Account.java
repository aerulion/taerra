package net.aerulion.taerra.mod.web.model;

public record Account(
    String uuid,
    String name,
    boolean teleport,
    boolean waypoints,
    boolean configure,
    boolean render,
    boolean hide) {}
