package net.aerulion.taerra.mod.web.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record Regions(
    @Nullable List<Integer> revealed, @Nullable List<Region> regions, boolean editable) {

  public static final Regions NONE = new Regions(List.of(), List.of(), false);

  public record Region(
      int id,
      String name,
      int[] label,
      @Nullable String color,
      @Nullable String fog,
      @Nullable Boolean discoverable,
      @Nullable Integer size,
      @Nullable Integer discoveries,
      @Nullable List<int[]> chunks) {}
}
