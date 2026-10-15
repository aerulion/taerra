package net.aerulion.taerra.mod.web.model;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record ServerSettings(
    long revision,
    List<SettingInfo> settings,
    List<WorldInfo> worlds,
    List<LayerInfo> layers,
    JsonObject values,
    @Nullable Map<String, Author> authors,
    @Nullable List<Editor> editors) {

  public record SettingInfo(
      String key, String kind, boolean editable, @Nullable Integer min, @Nullable Integer max) {}

  public record WorldInfo(String id, String defaultName, @Nullable String environment) {}

  public record LayerInfo(String id, String label, boolean present) {}

  public record Author(@Nullable String name, long at) {}

  public record Editor(String uuid, String name, String client) {}
}
