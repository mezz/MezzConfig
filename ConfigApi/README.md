# Config API

## Key Mapping Localization

Mods can add Minecraft key mappings to a display category with `IConfigDisplayCategoryBuilder.addKeyMapping(...)` or `addKeyMappings(...)`.

The config screen derives display text from the key mapping translation key:

- `key.example.action` for the action name
- `key.example.action.description` for the action description
- `key.example.action.context` for the active-context tooltip

In a development environment, missing key mapping localization is logged once per missing key.
