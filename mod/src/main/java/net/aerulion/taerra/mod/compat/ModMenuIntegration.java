package net.aerulion.taerra.mod.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.aerulion.taerra.mod.TaerraClient;
import net.aerulion.taerra.mod.gui.screen.SettingsScreen;
import org.jetbrains.annotations.NotNull;

public final class ModMenuIntegration implements ModMenuApi {

  @Override
  public @NotNull ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent -> new SettingsScreen(parent, TaerraClient.settings());
  }
}
