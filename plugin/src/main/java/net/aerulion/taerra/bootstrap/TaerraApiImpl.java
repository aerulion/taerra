package net.aerulion.taerra.bootstrap;

import net.aerulion.taerra.api.TaerraApi;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import org.jetbrains.annotations.NotNull;

record TaerraApiImpl(@NotNull MarkerRegistry markers) implements TaerraApi {}
