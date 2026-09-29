package net.aerulion.taerra.mod.web;

public interface WebMapListener {

  default void worldsChanged() {}

  default void playersChanged() {}

  default void markersChanged(final String world) {}
}
