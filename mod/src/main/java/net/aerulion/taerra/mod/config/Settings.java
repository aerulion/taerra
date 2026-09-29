package net.aerulion.taerra.mod.config;

public sealed interface Settings permits SettingsImpl {

  int MIN_SIZE = 64;
  int MAX_SIZE = 192;
  int MIN_ZOOM = 0;
  int MAX_ZOOM = 6;

  boolean minimap();

  void setMinimap(boolean minimap);

  int minimapSize();

  void setMinimapSize(int size);

  boolean minimapLeft();

  void setMinimapLeft(boolean left);

  int minimapZoom();

  void setMinimapZoom(int zoom);

  boolean minimapRotates();

  void setMinimapRotates(boolean rotates);

  boolean grayMap();

  void setGrayMap(boolean gray);

  boolean allWorldPlayers();

  void setAllWorldPlayers(boolean all);

  boolean waypointLabels();

  void setWaypointLabels(boolean labels);

  void save();
}
