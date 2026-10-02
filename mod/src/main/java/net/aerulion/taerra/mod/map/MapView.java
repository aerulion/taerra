package net.aerulion.taerra.mod.map;

import net.aerulion.taerra.mod.web.WebMap;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

public sealed interface MapView permits MapViewImpl {

  @Nullable
  String world();

  void setWorld(@Nullable String world);

  boolean isometric();

  int rotation();

  double spin();

  void setSpin(double spin);

  Projection projection(MapConfig config);

  void centerOn(MapConfig config, double x, double y, double z);

  void setView(MapConfig config, boolean isometric, int rotation);

  void pan(double dx, double dy);

  void glide(double dx, double dy);

  void stop();

  void zoomAt(double steps, double x, double y);

  void zoomToward(double target);

  long targetZoom();

  @Nullable
  Place placeAt(WebMap map, double x, double y);

  @Nullable
  MarkerHover hover();

  void select(@Nullable ChunkSelection selection, double @Nullable [][] outline, boolean erasing);

  void draw(
      GuiGraphicsExtractor graphics,
      WebMap map,
      int x0,
      int y0,
      int x1,
      int y1,
      int mouseX,
      int mouseY,
      float partialTick);
}
