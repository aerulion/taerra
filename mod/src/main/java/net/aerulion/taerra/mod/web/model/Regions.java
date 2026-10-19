package net.aerulion.taerra.mod.web.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record Regions(
    @Nullable List<String> revealed, @Nullable List<Region> regions, boolean editable) {

  public static final Regions NONE = new Regions(List.of(), List.of(), false);

  public record Region(
      String id,
      String name,
      int[] label,
      @Nullable String color,
      @Nullable String fog,
      @Nullable Boolean discoverable,
      @Nullable Integer size,
      @Nullable List<int[]> chunks) {}
}
