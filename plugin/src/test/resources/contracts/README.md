Cases for rules the plugin, the mod and the web map each implement. Every file is read by the tests of each side
that has the rule, so changing a rule means changing its cases here and every implementation until all pass.

| File                  | Plugin                          | Mod                         | Web map            |
|-----------------------|---------------------------------|-----------------------------|--------------------|
| `icon-tint.json`      | `MarkerIconsImpl.monochrome`    | `Pixels.monochrome`         | `pixels.js`        |
| `layer-scales.json`   | `LayerScale`                    | `SettingValues`             | `settingvalues.js` |
| `world-limits.json`   | `ConfigEditor.normalizeLimit`   | `SettingValues.badLimit`    | `settingvalues.js` |
| `region-colors.json`  |                                 | `ShapeEditor.freshColor`    | `regioncolors.js`  |
| `settings-rebase.json`|                                 | `SettingsDraft.rebase`      | `settingvalues.js` |

Pixels are ARGB hex, as Java reads them.
