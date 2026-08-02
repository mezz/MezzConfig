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

```java
general.addBoolean("enabled", true)
	.addLegacyName("oldEnabled")
	.build();

general.addEnum("mode", Mode.STANDARD)
	.build();
```

Use category and value legacy names when storage names change. If serialized
text also changed, add a legacy value migration function.

The core API exposes serialization, validation, storage names, and localization
keys. GUI/editor metadata and immediate versus staged editing behavior are
expected to live in a GUI integration layer.

## Sorting configs

Use `IConfigRegistration.createSortingConfig(...)` for string-backed sort-order
files. Saved sort orders can either preserve missing values by appending them
from the default comparator, or allow values to be removed.
