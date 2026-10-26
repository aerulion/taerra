package net.aerulion.taerra.api.region;

/** How a region looks on the map to players who have not discovered it. */
public enum RegionFog {
  /** No fog: the region is a named outline over the open map. */
  NONE,
  /** Thin fog the land shows through faintly. */
  VEIL,
  /** Fog that hides the land completely. */
  HIDE
}
