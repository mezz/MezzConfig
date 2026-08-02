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
a config file. Schemas contain storage categories, and categories contain config
values.

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

```java
general.addBoolean("enabled", true)
	.addLegacyName("oldEnabled")
	.build();

general.addEnum("mode", Mode.STANDARD)
	.build();
```

Use category and value legacy names when storage names change. If serialized
text also changed, add a legacy value migration function.

Values can also declare editor hints for integrations such as MezzConfigGui:

```java
general.addBoolean("enabled", true)
	.setEditMode(ConfigValueEditMode.IMMEDIATE)
	.addEditorCategory("quick")
	.addEditorCategory("advanced")
	.build();
```

Editor categories are presentation hints only. The value is still stored in its
schema category. Use {@code ConfigValueEditMode.RESTART} only for values whose
saved changes require a full game restart.

Use a batch updater when several config values should change together:

```java
List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> {
	updater.set(enabled, false);
	updater.set(mode, Mode.ADVANCED);
});
```

The batch is validated before any values are changed, and listeners are notified
after all changed values have updated.

The core API exposes serialization, validation, storage names, and localization
keys. GUI/editor metadata and immediate versus staged editing behavior are
expected to live in a GUI integration layer.

## Sorting configs

Use `IConfigRegistration.createSortingConfig(...)` for string-backed sort-order
files. Saved sort orders can either preserve missing values by appending them
from the default comparator, or allow values to be removed.
