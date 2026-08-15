# MezzConfig API

MezzConfig lets mods register lightweight client-owned config schemas,
server-authoritative config schemas, and string-backed client sort orders
through loader-discovered plugins.

## Registering a plugin

Implement `IConfigPlugin`.

- Forge and NeoForge discover plugins annotated with `@ConfigPlugin`; the
  plugin class must have a public no-argument constructor.
- Fabric discovers plugins from the `mezz_config_plugin` entrypoint in
  `fabric.mod.json`.

The plugin's `getModId()` owns the config subdirectory. During
`registerConfigFiles(...)`, use `IConfigRegistration` to create schema builders
or sorting configs.

Client config plugins are loaded only on the physical client, so they may use
client-only classes.

For server-authoritative settings, implement `IServerConfigPlugin` as well as,
or instead of, `IConfigPlugin`.

- Forge and NeoForge discover server plugins annotated with
  `@ServerConfigPlugin`.
- Fabric discovers them from the `mezz_config_server_plugin` entrypoint.

Server plugins load on dedicated servers and clients. Their registration code
and referenced classes must therefore be safe to load without client-only
Minecraft classes. A common-safe class may implement both plugin interfaces;
on Forge and NeoForge, annotate it with both annotations, and on Fabric list it
under both entrypoint keys.

A physical client may invoke server registration separately to construct its
synchronized client view and its integrated-server authoritative state. Keep
registration repeatable and limited to declaring schemas; do not use one-time
side effects or guards that skip a later registration call.

## Config schemas

Use `IConfigRegistration.createSchemaBuilder(...)` to create a schema backed by
a config file. Use `createClientWorldSchemaBuilder(...)` when the values should
be separate for each singleplayer world or multiplayer server. Schemas contain
storage categories, and categories contain config values.

Normal schemas use the registered file as a modpack-owned default and keep each
player's choices in a separate profile directory:

```text
config/<mod-id>/<file-name>                         # pack default
config/<mod-id>/players/<profile-uuid>/<file-name>  # player choices
```

MezzConfig generates the default file when it is missing. A modpack can edit
that file, remove values it does not want to customize, and distribute it
without including the `players` directory. At runtime, declared code defaults
are loaded first, then the pack default, then the player's file. The player
file is created only after a player changes a value, and from that point it
takes precedence so a pack update cannot overwrite the player's choices.

Client-world schemas follow the same rule. Their distributable defaults are in
`config/<mod-id>/world/default`, while profile-specific values remain separated
under `players/<profile-uuid>/world/local` or `players/<profile-uuid>/world/server`.

### Malformed-file recovery

Config files are read as bounded UTF-8 text (at most 4 MiB and 100,000 lines).
Missing categories and values are valid: they inherit the value from the lower
layer, or the declared code default when there is no lower layer. Valid entries
elsewhere in a damaged file are still applied. A partially successful custom
deserializer likewise keeps its usable result while reporting the diagnostic.

Syntax errors, invalid serialized values, unknown categories or keys, duplicate
values, legacy migrations, invalid UTF-8, and files over the read limits cause
the affected file to be corrected to its canonical form. Before replacement,
MezzConfig copies the original beside it as `<filename>.bak.1`; it retains at
most five numbered backups. Identical unchanged failures do not create another
backup or repeat a failed correction attempt. Correction uses the same atomic
replacement path as ordinary saves.

Only the file containing the problem is replaced. In particular, recovery of a
player, client-world, or world-server overlay does not modify its distributable
pack default. Ordinary filesystem read/open failures are treated as potentially
transient: they are diagnosed, but the path is not backed up, replaced, or
deleted. File-watcher reloads and delayed saves are serialized per schema so
they cannot observe a half-written correction.

### Server-authoritative schemas

Use `IServerConfigRegistration.createServerSchemaBuilder(...)` for settings
whose effective value is owned by the server. This is distinct from a
client-world schema: a client-world schema merely selects a different local
preference file for each connection, while a server schema is loaded, validated,
persisted, and authorized by the server.

Server schemas use these locations:

```text
config/<mod-id>/server/default/<file-name>  # distributable default
<world>/serverconfig/<mod-id>/<file-name>   # active world's authoritative values
```

The world file is created when the server starts. Declared code defaults are
loaded first, then the distributable default, then the world file. Connected
clients do not read either server file; they receive the server's complete
effective snapshot in memory when they join and whenever the values change.
Integrated servers likewise keep their authoritative schema state separate
from the client-facing snapshot.
Editing the world file is detected and synchronized automatically. Large
snapshots and update requests are split into bounded network fragments and
reassembled before the complete batch is validated or applied. The internal
protocol accepts out-of-order and interleaved messages, rejects duplicates and
inconsistent metadata, and limits each complete message to 1 MiB and 64
fragments. Each peer/direction may retain at most four incomplete messages and
2 MiB of incomplete data; incomplete messages expire after 10 seconds. Decoded
messages are limited to 4,096 values, 256 KiB per serialized value, and smaller
field-specific bounds for identifiers and diagnostics. These controls are
internal and intentionally not caller-configurable.

On a client, `IConfigSchema.isActive()` becomes true after the first snapshot.
`canEdit()` is true when the server reports that the player has its configured
operator permission level, and is refreshed when that permission changes. This
flag helps config editors disable controls, but the server rechecks permission
and validates every requested value.

Use `requestBatchUpdate(...)` in config editors:

```java
CompletableFuture<Void> result = schema.requestBatchUpdate(updater -> {
	updater.set(enableCheatModeForOp, true);
	updater.set(enableCheatModeForCreative, false);
});
```

For client schemas, the future is already complete after the normal local
update. For server schemas, current values remain unchanged until an accepted
request returns in an authoritative snapshot. The future completes
exceptionally when the player lacks permission, a value is rejected, the
connection closes, a send fails, the server does not support the request, or no
response arrives within 15 seconds. At most 128 remote update requests may be
pending at once. Every synchronized batch is fully decoded and validated before
any value changes; if any known value is invalid, none of that batch is applied.
Direct `IConfigValue.set(...)` and `IConfigSchema.batchUpdate(...)` calls are rejected
for server schemas so an integrated client cannot bypass server authority.

The server-config channel is optional. Connecting to a server without it still
succeeds; an attempted remote edit fails through its future. The current
fragment envelope is protocol version 2 and is intentionally incompatible with
the earlier unreleased first/last-fragment format.

Supported built-in value helpers include:

- strings
- booleans
- integers, packed RGB or ARGB colors, longs, and finite doubles
- optional bounds for numeric values
- enums, including restricted sets of enum values
- typed lists of strings, booleans, integers, packed colors, longs, finite doubles, or enums
- custom serializers

Built-in helpers use the same `addValue(...)` and `addList(...)` serializer paths
as custom value types. Implement `IConfigValueSerializer<T>` and pass it to one
of these methods to add a new type without registering it with MezzConfig.
Config value types must be effectively immutable and have stable `equals`
behavior while MezzConfig holds them. Built-in list containers are copied when
created, loaded, or updated and are exposed as unmodifiable snapshots.

Deserializer results have three explicit states: success has a value and no
diagnostics, partial success has a usable value and one or more diagnostics,
and failure has no value and one or more diagnostics. Use the corresponding
`IDeserializeResult.success(...)`, `partialSuccess(...)`, or `failure(...)`
factory; invalid state combinations are rejected.

List helpers expose their element serializer through `IConfigListValueSerializer`,
so integrations can edit list elements individually without GUI-specific API in
MezzConfig.

Custom key-value entry types can implement
`IConfigKeyValueSerializer`. It exposes serializers for both components and
methods to split and rebuild the entry. This supports map-style
editors while preserving ordered lists, domain value types, and custom storage
formats. Use `addKeyValueList(...)` to create an ordered list with this
structure.

List serializers expose `ConfigListOrdering`. Lists are ordered by default;
pass `ConfigListOrdering.UNORDERED` to `addList(...)` or
`addKeyValueList(...)` when integrations do not need to offer reordering
controls. MezzConfig still preserves the physical order in the file.

Colors use the `PackedColor` value type, which identifies whether its packed
integer uses `0xRRGGBB` or `0xAARRGGBB`. This lets integrations add standard
color swatches and pickers, including alpha controls only for ARGB colors.

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
`IConfigSchema.isActive()` before showing or enabling context-specific schemas.
Use `getPath()` only when the local backing-file path itself is relevant;
synchronized remote server schemas are active but intentionally return an empty
path.

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

Values can also declare editor metadata and restart behavior:

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
`ConfigValueRestartRequirement.GAME_RESTART` when a saved change must wait for
that lifecycle boundary. `getValue()` continues to return the effective value;
`getPendingValue()` returns the saved selection.

Use a batch updater when several config values should change together:

```java
List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> {
	updater.set(enabled, false);
	updater.set(mode, Mode.ADVANCED);
});
```

The batch is validated before any values are changed. Persistence is scheduled
for all saved changes, while listeners are notified only for values that became
effective immediately.
`IConfigValue.set(...)` returns `true` for a change and `false` for a valid
unchanged value. It throws `IllegalArgumentException` for invalid values and
`IllegalStateException` when a context-specific schema is inactive.
Config editor integrations should normally use `requestBatchUpdate(...)`
instead, because it also handles server-authoritative schemas.

Listener registration returns an unsubscribe callback. Each owner should retain
and invoke its callbacks during teardown. Listeners run synchronously on the
thread applying the change; a failing listener is logged without preventing
persistence or later listeners from running.

The core API exposes serialization, validation, storage names, localization
keys, lightweight editor category hints, and edit-mode hints. GUI-specific
widgets, layout, staging, and apply behavior are expected to live in a GUI
integration layer.

## Sorting configs

Use `IConfigRegistration.createSortingConfig(...)` for string-backed sort-order
files. Saved sort orders can either preserve missing values by appending them
from the default comparator, or allow values to be removed.

Sort-order files use the same pack-default and profile-specific locations as
normal schemas. The default file is generated the first time the complete set
of sortable values is available; later user changes are written only to the
profile-specific file.

Sortable values must be effectively immutable with stable equality and hash
codes. Sorting methods return unmodifiable, duplicate-free snapshots and
reconcile the saved preference against the runtime values supplied to each
call. `ISortingConfig.setSortedValues(...)` returns `true` only for a change,
returns `false` for an unchanged order, and rejects duplicate values. Change
listeners run synchronously after persistence is attempted; a failing listener
is logged without preventing later listeners from running.

When removal is enabled, sorting configs persist explicitly hidden known values
separately from the visible order. A value omitted from an update is hidden only
if it was present in the latest runtime collection. Values discovered later are
visible by default.
