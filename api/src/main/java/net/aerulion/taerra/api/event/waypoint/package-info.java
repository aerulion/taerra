/**
 * Events about waypoints: the shared ones in the {@code waypoints} layer and its groups, and the
 * personal ones only their owner sees.
 *
 * <p>They fire for every way a waypoint changes: the web map and the mod, the {@code /taerra
 * waypoint} command, imports, and the death point TAERRA sets. A waypoint's id follows its name, so
 * renaming one deletes it and creates it under the new name, and so does moving it between shared
 * and personal.
 */
package net.aerulion.taerra.api.event.waypoint;
