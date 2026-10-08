/**
 * Markers other plugins draw on the TAERRA map, grouped into layers viewers switch on and off.
 *
 * <p>Get a layer from {@link net.aerulion.taerra.api.marker.MarkerRegistry}, create markers with
 * the factories on {@link net.aerulion.taerra.api.marker.Marker} and add them to the layer. The web
 * map and the mod show the change within about a second. Markers only one player sees, like their
 * homes, go in a {@link net.aerulion.taerra.api.marker.PlayerMarkerLayer} instead, which asks your
 * plugin for them when that player looks.
 */
package net.aerulion.taerra.api.marker;
