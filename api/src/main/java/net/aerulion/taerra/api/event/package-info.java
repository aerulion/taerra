/**
 * Bukkit events TAERRA fires, listened to with {@link org.bukkit.event.EventHandler} like any other
 * event.
 *
 * <p>Every event fires on the server thread. Most report a change after it was saved, wherever it
 * came from: the web map, the mod, a command or TAERRA itself. {@link
 * net.aerulion.taerra.api.event.web.WebTeleportEvent} fires before its teleport and can stop it.
 *
 * <p>The abstract classes group related events and hold what they share. Bukkit only listens to the
 * concrete events, so listen to each one you need.
 */
package net.aerulion.taerra.api.event;
