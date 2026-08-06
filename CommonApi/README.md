# MezzConfig API

MezzConfig lets mods register lightweight client-side config schemas and
string-backed sort orders through loader-discovered config plugins.

## Registering a plugin

Implement `IConfigPlugin`.

- Forge and NeoForge discover plugins annotated with `@ConfigPlugin`; the
  plugin class must have a public no-argument constructor.
- Fabric discovers plugins from the `mezz_config_plugin` entrypoint in
  `fabric.mod.json`.

The plugin's `getModId()` owns the config subdirectory. During
`registerConfigFiles(...)`, use `IConfigRegistration` to create schema builders
or sorting configs.

In this initial version, MezzConfig discovers and loads config plugins on the
client. The API is intentionally common enough to support server-side configs in
the future.

## Config schemas

Use `IConfigRegistration.createSchemaBuilder(...)` to create a schema backed by
a config file. Use `createClientWorldSchemaBuilder(...)` when the values should
be separate for each singleplayer world or multiplayer server. Schemas contain
storage categories, and categories contain config values.

Supported built-in value helpers include:

- strings
- booleans
- integers, ARGB colors, longs, and finite doubles
- optional bounds for numeric values
- enums, including restricted sets of enum values
- typed lists of strings, booleans, integers, ARGB colors, longs, finite doubles, or enums
- custom serializers

List helpers expose their element serializer through `IConfigListValueSerializer`,
so integrations can edit list elements individually without GUI-specific API in
MezzConfig.

The localization path passed to the schema builder is combined with category
and value names. Define `<path>.<category>` and
`<path>.<category>.description` for each category's display name and
description. Generated config files include these as comments above each
storage section, while the stable category name remains in `[brackets]`.

```java
general.addBoolean("enabled", true)
	.addLegacyName("oldEnabled")
	.build();

general.addString("filter", "")
	.addLegacyValue("oldGeneral", "oldFilter")
	.build();

general.addEnum("mode", Mode.STANDARD)
	.build();
```

Client-world schemas are inactive until the client is connected to a world or
server. While inactive, values read as defaults and updates are rejected because
there is no backing file to save. Config editors should check
`IConfigSchema.getPath()` before showing or enabling context-specific schemas.

Schemas expose the owning mod id through `IConfigSchema.getModId()`, so
integrations can group schemas by mod and create default config screens without
adding GUI-specific API to MezzConfig.

Generated config screens can get the active config manager from
`net.mezzdev.config.api.files.ConfigManagers.getConfigManager()`.

Use value legacy names when storage names change. If a value moved from another
storage category, declare the old category and value name on that value. If
serialized text also changed, add a legacy value migration from the old storage
location. Legacy migrations must come from an old category, old value name, or
both. If the serialized format changes without a storage name change, use a
serializer that accepts both formats, or move to a new storage name and migrate
from the old one.

Values can also declare editor hints for integrations such as MezzConfigGui:

```java
IConfigEditorCategoryBuilder quick = schemaBuilder.addEditorCategory("quick");
IConfigEditorCategoryBuilder advanced = schemaBuilder.addEditorCategory("advanced");

general.addBoolean("enabled", true)
	.setEditMode(ConfigValueEditMode.IMMEDIATE)
	.addEditorCategory(quick)
	.addEditorCategory(advanced)
	.build();

general.addBoolean("needsRestart", false)
	.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
	.build();
```

Editor-only categories are presentation hints and are not written to the config
file. The value is still stored in its schema category. Storage categories can
also be used as editor categories. Config editors should use
`IConfigSchema.getEditorCategories()` as the category display order.
`ConfigValueEditMode` describes when editors should save changes. Use
`ConfigValueRestartRequirement.WORLD_RESTART` or
`ConfigValueRestartRequirement.GAME_RESTART` only to explain when changed values
take effect. MezzConfig still updates values when they change through the API or
config file; mods that only apply a value at startup or world load should read
it during that lifecycle.

Use a batch updater when several config values should change together:

```java
List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> {
	updater.set(enabled, false);
	updater.set(mode, Mode.ADVANCED);
});
```

The batch is validated before any values are changed, and listeners are notified
after all changed values have updated.

The core API exposes serialization, validation, storage names, localization
keys, lightweight editor category hints, and edit-mode hints. GUI-specific
widgets, layout, staging, and apply behavior are expected to live in a GUI
integration layer.

## Sorting configs

Use `IConfigRegistration.createSortingConfig(...)` for string-backed sort-order
files. Saved sort orders can either preserve missing values by appending them
from the default comparator, or allow values to be removed.
