# MezzConfig API

MezzConfig lets mods directly create typed client-owned and server-owned config
schemas, plus string-backed client sort orders.

## Supported API boundary

The supported API is the non-internal surface published by the `CommonApi`
module under `net.mezzdev.config.api`. Types annotated with
`@ApiStatus.Internal` are runtime integration details. Public classes in the
`Common` implementation module and the Fabric, Forge, and NeoForge loader
modules are also internal and may change without API compatibility guarantees.
Depend on those modules through the loader artifact, but compile integrations
only against the supported `CommonApi` types.

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
directory. `build()` loads a client schema before it returns, so its values can
be consumed immediately. A schema builder already has a complete type and
location when it is returned; there are no ownership, scope, or location
setters on the builder.

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

Choose a factory for the complete schema behavior you need:

- `createClientSchemaBuilder(...)` creates client preferences that remain active across worlds and connections.
- `createClientPerWorldSchemaBuilder(...)` creates separate client preferences for each world or server.
- `createServerSchemaBuilder(...)` creates authoritative settings stored with the active world and synchronized to clients.
- `createClientSchemaBuilderAtLocation(...)` creates an always-active client schema at one explicit file location.

Automatically placed client schemas use one local file and are not synchronized:

```text
config/<mod-id>/client/<file-name>
```

For an explicit client file, pass its complete path before the builder is
created:

```java
IConfigSchemaBuilder builder = configs.createClientSchemaBuilderAtLocation(
	sharedConfigDirectory.resolve("example.ini"),
	"example_mod.config.shared"
);
```

MezzConfig does not append a mod id or file name to this path. Relative paths
are captured as normalized absolute paths when the factory is called. Explicit
locations are an alternative to automatic placement and cannot be combined
with per-world behavior. The location must be readable and writable when the
schema is built: the initial load or file creation is synchronous and throws
`UncheckedIOException` on failure.

After a successful build, edits update the in-memory values immediately and
schedule persistence after a two-second quiet period. If the path later becomes
read-only, disconnected, or otherwise unavailable, a failed delayed save does
not roll back the accepted edit and cannot report failure to the original
editing call; it is not retried until another change schedules a save. Automatic
reload also depends on the platform file watcher. Unwatchable or missing parent
directories are diagnosed and retried periodically, so external changes may go
unnoticed while the location is unavailable. A reload that reaches an
unreadable file fails with `UncheckedIOException`; MezzConfig does not back up,
replace, or delete a file for an ordinary filesystem access failure.

Register server schemas on both physical sides from common setup.
The dedicated or integrated server uses its file-backed authoritative instance;
a remote client keeps the same schema in memory and activates it from server
snapshots without reading or creating the server's world files.

## Config schemas

Schemas contain storage categories, and categories contain config values.
`IConfigCategory.getConfigValues()` returns an immutable list in builder
insertion order, which config screens can use directly for stable layout.
Client-owned world schemas use these locations:

```text
config/<mod-id>/client/world/default/<file-name>       # distributable default
config/<mod-id>/client/world/local/<world>/<file-name> # singleplayer world
config/<mod-id>/client/world/server/<server>/<file-name> # multiplayer server
```

MezzConfig generates a missing default file. Declared code defaults are loaded
first, followed by the distributable default and then the active world's file.

The supported runtime states are:

| Schema type and context | Active | Local updates | Local path |
| --- | --- | --- | --- |
| `CLIENT` on a physical client | yes | yes | installation or explicit file |
| `CLIENT` on a dedicated server | no | no | none |
| `CLIENT_PER_WORLD` while disconnected | no | no | none |
| `CLIENT_PER_WORLD` in a world or server connection | yes | yes | context-specific client file |
| `CLIENT_PER_WORLD` on a dedicated server | no | no | none |
| `SERVER` before a local world or remote snapshot | no | no | none |
| `SERVER` authoritative for a local world | yes | yes | world `serverconfig` file |
| `SERVER` synchronized from a remote server | yes | no | none |

An inert client declaration on a dedicated server remains safe to build from
common initialization code but is not returned by `Configs.getSchemas()`.

### Malformed-file recovery

Config files are read as bounded UTF-8 text (at most 4 MiB and 100,000 lines).
The same bounds are checked before MezzConfig creates or replaces a config or
sort-order file. A schema build, schema update, or file-backed sort reconciliation
that would exceed them is rejected with `IllegalArgumentException` before its
public state changes.
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

Use `IConfigRegistration.createServerSchemaBuilder(...)` for settings whose
effective value is owned by the server. This is distinct from a
client-world schema: a client-world schema merely selects a different local
preference file for each connection, while a server schema is loaded, validated,
and persisted by the server.

Server schemas use these locations:

```text
config/<mod-id>/server/world/default/<file-name>  # distributable default
<world>/serverconfig/<mod-id>/<file-name>   # active world's authoritative values
```

The world file is created when the server starts. Declared code defaults are
loaded first, then the distributable default, then the world file. Connected
clients do not read either server file; they receive the server's complete
effective snapshot in memory when they join and whenever the values change.
Only effective values are synchronized. On a remote client,
`getPendingValue()` therefore equals `getValue()` even when the authoritative
server has a different saved value waiting for a restart.
Editing the world file is detected and synchronized automatically. Client-owned
files use a 500-millisecond quiet period, while server-owned files use a separate
watcher profile with a two-second quiet period so a multi-step editor save can
settle before MezzConfig reloads and broadcasts it. Large snapshots are split
into bounded network fragments and reassembled before the complete batch is
validated or applied. The internal
protocol accepts out-of-order and interleaved messages, rejects duplicates and
inconsistent metadata, and limits each complete message to 1 MiB and 64
fragments. A client may retain at most four incomplete messages and
2 MiB of incomplete data; incomplete messages expire after 10 seconds. Decoded
messages are limited to 4,096 values, 256 KiB per serialized value, and smaller
field-specific bounds for identifiers. These controls are
internal and intentionally not caller-configurable.

On a client, `IConfigSchema.isActive()` becomes true after the first snapshot.
The synchronized instance is a read-only runtime mirror: `IConfigValue.set(...)`
and `IConfigSchema.batchUpdate(...)` reject non-empty updates because it has no
local backing file. On the authoritative server instance, those ordinary update
methods validate, persist, notify, and synchronize changes normally.

Remote editing is intentionally outside MezzConfig's schema and networking
contracts. An editor integration can define its own optional client-to-server
protocol, authorization policy, request lifecycle, diagnostics, and pending
value snapshot. Its server handler should resolve and validate proposed values,
then apply accepted values to the authoritative schema with `batchUpdate(...)`.
MezzConfig's one-way synchronization will broadcast any resulting effective
changes independently.

The server-config channel is optional. Connecting to a server without it still
succeeds, but the client keeps declared defaults instead of receiving server
values.

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

Deserializer outcomes are determined by result presence and diagnostics:
success has a value and no diagnostics, partial success has a usable value and
one or more diagnostics, and failure has no value and one or more diagnostics.
Use the corresponding `IDeserializeResult.success(...)`,
`partialSuccess(...)`, or `failure(...)` factory; invalid result and diagnostic
combinations are rejected.

List helpers expose their element serializer through `IConfigListValueSerializer`,
so integrations can edit list elements individually without GUI-specific API in
MezzConfig. Files and server synchronization use canonical structured arrays,
recursively applying each element serializer. After loading an array, MezzConfig
validates the reconstructed list with the list serializer's `isValid` method.
Container-level `serialize` and `deserialize` use the same structured array
representation for direct serializer calls.

Custom key-value entry types can implement
`IConfigKeyValueSerializer`. It exposes serializers for both components and
methods to split and rebuild the entry. This supports map-style
editors while preserving ordered lists, domain value types, and custom storage
formats. Valid entries must split into valid components and rebuild to equal
entries; integrations validate newly combined components against the complete
entry serializer. Pass it to `addList(...)` to create a list with this structure.

List serializers expose `ConfigListOrdering`. Lists are ordered by default;
pass `ConfigListOrdering.UNORDERED` to `addList(...)` when integrations do not
need to offer reordering controls. MezzConfig still preserves the physical
order in the file.

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
adding GUI-specific API to MezzConfig. `IConfigSchema.getId()` returns the
schema's stable opaque storage identity. The tuple of mod id, schema type, and
schema id can address a schema without relying on registration order or its
currently active path.

Generated config screens can enumerate registered schemas with
`Configs.getSchemas()`.

Use value legacy names when storage names change. If a value moved from another
storage category, declare the old category and value name on that value. If
the serialized format or value type also changed, add a typed legacy value
migration with the old serializer and a converter to the current type. MezzConfig
decodes old scalar and structured values through that serializer before calling
the converter, so migration code does not parse config-file syntax. Legacy
migrations must come from an old category, old value name, or both. If the
serialized format changes without a storage name change, use a serializer that
accepts both formats, or move to a new storage name and migrate from the old one.

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
for all saved changes. Single-value effective listeners registered with
`IConfigValue.addListener(...)` are notified only for values that became
effective immediately. Pending listeners registered with
`IConfigValue.addPendingListener(...)` are notified for every saved-value
change, including changes waiting for a restart.

Use `IConfigValue.addBatchListener(...)` or `addPendingBatchListener(...)` when
one value-specific integration needs the complete batch. These listeners run
exactly once only when that value participates. Use
`IConfigSchema.addBatchListener(...)` or `addPendingBatchListener(...)` to
observe every non-empty schema batch exactly once, regardless of which values
participated. No listener runs for an unchanged batch.

Notifications are synchronous after the complete state is committed. Local
updates schedule persistence before notifying; loads and synchronized snapshots
notify after applying their complete state. Pending notifications run before
effective notifications from the same operation. Within each kind, participating values
are visited in batch order: a value's single listener runs before its scoped
batch listener, and schema batch listeners run after all value listeners.
Listeners at one scope run in registration order. Failures are logged and
isolated. The returned removal callbacks are idempotent; registration changes
during a callback affect later notification snapshots. A listener may make a
reentrant update, which dispatches a separate nested batch synchronously before
the outer notification resumes, so callers must prevent update cycles.

`IConfigValue.set(...)` returns `true` for a change and `false` for a valid
unchanged value. It throws `IllegalArgumentException` for invalid values and
`IllegalStateException` when its schema has no active local backing file.

Built schemas, values, and sorting configs are thread-safe; batches are atomic,
but concurrent operations are unordered. Listeners run on the applying thread,
and builders are not thread-safe. Retain listener removal callbacks for teardown.

The core API exposes serialization, validation, storage names, localization
keys, lightweight editor category hints, and edit-mode hints. GUI-specific
widgets, layout, staging, and apply behavior are expected to live in a GUI
integration layer.

## Sorting configs

Use the three-argument `IConfigRegistration.createSortingConfig(...)`
convenience overload for string sort orders. For any other effectively immutable
value type, use the generic overload with an `IConfigValueSerializer<T>`:

```java
ISortingConfig<ResourceLocation> order = configs.createSortingConfig(
	"ingredient-order.txt",
	resourceLocationSerializer,
	Comparator.naturalOrder(),
	true
);
```

`ISortingConfig` instances are created and owned by the MezzConfig runtime;
integrations consume them but do not implement the interface. Saved sort orders
can either preserve missing values by appending them from the default comparator,
or allow values to be removed.

Sort-order files are installation-scoped under
`config/<mod-id>/client/<file-name>`. The file is generated the first time the
complete set of sortable values is available. Each schema and file-backed
sorting config must resolve to a unique normalized absolute path. MezzConfig
rejects duplicate schema paths, duplicate sorting paths, and schema/sorting
collisions before the conflicting config reads or writes the file. Dedicated
server sorting configs remain in memory and do not reserve paths.

Sortable values must be effectively immutable with stable equality and hash
codes. A generic sorting serializer has the same validation, determinism, and
round-trip requirements as a config-value serializer. Its stored text is also
the value's persistent identity: equal values must serialize identically, and
unequal values must not share serialized text. MezzConfig validates runtime and
loaded values at this boundary. Invalid persisted entries are diagnosed,
skipped, backed up, and corrected while valid neighboring entries remain usable.

Sorting methods return unmodifiable, duplicate-free snapshots and reconcile the
saved preference against the runtime values supplied to each call.
`ISortingConfig.setSortedValues(allValues, sortedValues)` takes the complete
runtime value set with every update, returns `true` only for a change, returns
`false` for an unchanged order, and rejects duplicates or sorted values outside
the complete set. It also rejects an order that cannot fit in a readable
sort-order file. Change listeners run synchronously after persistence is
attempted; a failing listener is logged without preventing later listeners from
running.

When removal is enabled, sorting configs persist explicitly hidden known values
separately from the visible order. A value omitted from an update is hidden only
if it is present in that update's complete runtime collection. Values discovered
later are visible by default.
