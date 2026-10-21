package net.aerulion.taerra.web.http;

public sealed interface TaerraWebServer permits TaerraWebServerImpl {

  void start();

  void stop();

  void restart();

  boolean running();

  void refreshMarkers();
}
