package net.aerulion.taerra.mod.web;

import java.util.List;
import net.aerulion.taerra.mod.web.model.ServerSettings;

public interface WebMapListener {

  default void worldsChanged() {}

  default void playersChanged() {}

  default void markersChanged(final String world) {}

  default void regionsChanged(final String world) {}

  default void settingsChanged(final long revision) {}

  default void settingsEditors(final List<ServerSettings.Editor> editors) {}
}
