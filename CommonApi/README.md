# MezzConfig API

MezzConfig lets mods directly create typed client-owned and server-owned config
schemas, plus string-backed client sort orders.

## Registering configs

Create one registration for the owning mod, declare values, and build the
schema before consuming them:

```java
IConfigRegistration configs = Configs.forMod("example_mod");
IConfigSchemaBuilder builder = configs.createClientSchemaBuilder(
	"integrations.ini",
	"example_mod.config.integrations"
);
IConfigValue<Boolean> enableIntegration = builder
	.addCategory("general")
	.addBoolean("enableIntegration", true)
	.build();
IConfigSchema schema = builder.build();
```

`Configs.forMod(...)` uses the active mod loader's conventional config
directory. Its overload accepting a `Path` lets a mod deliberately store its
configuration under another root and supports tests or embedded applications.
`build()` loads an installation-scoped schema before it returns, so its values
can be consumed immediately.

Build client and server schemas together from your mod's common initializer or
mod constructor. On a dedicated server, client schema declarations remain safe
to run so shared registration code can initialize its fields, but the resulting
schemas stay inactive and default-backed: MezzConfig does not register them,
create their files, or start a client file watcher. Client sort orders similarly
stay in memory without file access.

Register schemas before client config-screen setup when using automatically
generated screens. MezzConfigGUI creates Forge and NeoForge's automatic
config-screen factories during client setup. A schema built later remains
registered and usable through MezzConfig, but it is not included in those
automatically generated screens.

The builder factory selects who owns the values:

- `createClientSchemaBuilder(...)` creates locally owned client settings.
- `createServerSchemaBuilder(...)` creates server-owned settings.

Both builders use `ConfigScope.INSTALLATION` by default. Installation schemas
use one local file and are not synchronized:

```text
config/<mod-id>/client/<file-name>
config/<mod-id>/server/<file-name>
```

Call `setScope(ConfigScope.WORLD)` before `build()` when values belong to a
world. Ownership and scope are independent: a client-owned world schema stores
local preferences separately for each singleplayer world or multiplayer
server, while a server-owned world schema is authoritative for the active world
and synchronized to connected clients.

Register server-owned world schemas on both physical sides from common setup.
The dedicated or integrated server uses its file-backed authoritative instance;
a remote client keeps the same schema in memory and activates it from server
snapshots without reading or creating the server's world files.

## Config schemas

Schemas contain storage categories, and categories contain config values.
Client-owned world schemas use these locations:

```text
config/<mod-id>/client/world/default/<file-name>       # distributable default
config/<mod-id>/client/world/local/<world>/<file-name> # singleplayer world
config/<mod-id>/client/world/server/<server>/<file-name> # multiplayer server
```

MezzConfig generates a missing default file. Declared code defaults are loaded
first, followed by the distributable default and then the active world's file.

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
client-world or world-server file does not modify its distributable
pack default. Ordinary filesystem read/open failures are treated as potentially
transient: they are diagnosed, but the path is not backed up, replaced, or
deleted. File-watcher reloads and delayed saves are serialized per schema so
they cannot observe a half-written correction.

### Server-authoritative schemas

Use `IConfigRegistration.createServerSchemaBuilder(...)` with
`setScope(ConfigScope.WORLD)` for settings
whose effective value is owned by the server. This is distinct from a
client-world schema: a client-world schema merely selects a different local
preference file for each connection, while a server schema is loaded, validated,
persisted, and authorized by the server.

Server schemas use these locations:

```text
config/<mod-id>/server/world/default/<file-name>  # distributable default
<world>/serverconfig/<mod-id>/<file-name>   # active world's authoritative values
```

The world file is created when the server starts. Declared code defaults are
loaded first, then the distributable default, then the world file. Connected
clients do not read either server file; they receive the server's complete
effective snapshot in memory when they join and whenever the values change.
Editing the world file is detected and synchronized automatically. Client-owned
files use a 500-millisecond quiet period, while server-owned files use a separate
watcher profile with a two-second quiet period so a multi-step editor save can
settle before MezzConfig reloads and broadcasts it. Large
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
update. For remote server-owned world schemas, current values remain unchanged until an accepted
request returns in an authoritative snapshot. The future completes
exceptionally when the player lacks permission, a value is rejected, the
connection closes, a send fails, the server does not support the request, or no
response arrives within 15 seconds. At most 128 remote update requests may be
pending at once. Every synchronized batch is fully decoded and validated before
any value changes; if any known value is invalid, none of that batch is applied.
Direct `IConfigValue.set(...)` and `IConfigSchema.batchUpdate(...)` calls are rejected
for server-owned world schemas so an integrated client cannot bypass server authority.

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
MezzConfig. Files and server synchronization use canonical structured arrays,
recursively applying each element serializer. After loading an array, MezzConfig
validates the reconstructed list with the list serializer's `isValid` method.
Container-level `serialize` output is not used for structured storage;
container-level `deserialize` remains supported for legacy scalar values.

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

Generated config screens can enumerate registered schemas with
`Configs.getSchemas()`.

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

Sort-order files are installation-scoped under
`config/<mod-id>/client/<file-name>`. The file is generated the first time the
complete set of sortable values is available.

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
