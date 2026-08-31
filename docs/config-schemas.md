# Config schemas

## Why use a config schema?

Settings that look similar may belong to different places. A UI preference
should follow the player's installation, a preference for one world or server
should change with that context, and a gameplay rule should be owned by the
server so every connected player sees the same value.

A schema groups settings that share one owner and lifetime. Declaring that
boundary lets MezzConfig select the right file, keep world-specific preferences
separate, and synchronize server-owned values without each setting implementing
those rules itself. It also prevents code on the wrong side from changing a
value it does not own.

Choose the schema type based on that ownership. Categories and values work the
same way in every type.

[Back to the API guide](API.md)

## Register schemas from common setup

Create one `IConfigRegistration` for your mod and use it for every schema and
sorting config that mod owns.

```java
IConfigRegistration configs = Configs.forMod("example_mod");

IConfigSchemaBuilder client = configs.createClientSchemaBuilder(
	"client.ini",
	"example_mod.config.client"
);
IConfigSchemaBuilder server = configs.createServerSchemaBuilder(
	"server.ini",
	"example_mod.config.server"
);
```

Declare both client and server schemas during common initialization on both
physical sides. This keeps one declaration path for Fabric, Forge, and
NeoForge. MezzConfig makes declarations inert where their owner is unavailable:
a client schema built on a dedicated server remains default-backed and does not
touch a client file.

Build every value before calling `IConfigSchemaBuilder.build()`. Build schemas
before config-screen registration so editor integrations can discover them.

## Choose a schema type

| Type | Active context | Writable by | Local file |
| --- | --- | --- | --- |
| `CLIENT` | A physical client. | The client. | The physical client. |
| `CLIENT_PER_WORLD` | A client in a world or server connection. | The client. | The active client context. |
| `SERVER` | A server, or a client after a snapshot. | The authoritative server. | The authoritative server. |

Use `IConfigSchema.isActive()` to decide whether a schema currently supplies
values for the running context. Use `getPath()` only when you specifically need
to know whether this process owns a local file. An active synchronized server
schema on a remote client deliberately has no local path.

Calls to `IConfigValue.set(...)` and `IConfigSchema.batchUpdate(...)` require an
active local backing file. They reject updates to inactive schemas and remote
server mirrors.

## File locations and default layers

Conventional schemas are stored below Minecraft's config directory.

### Client

An installation-wide client schema uses one file:

```text
config/<mod-id>/client/<file-name>
```

For a complete custom path, use
`createClientSchemaBuilderAtLocation(path, localizationPath)`. The supplied
path is the config file itself; MezzConfig does not append a mod id or file
name.

### Client per world

Client-per-world schemas use a distributable default and one active file for
the current context:

```text
config/<mod-id>/client/world/default/<file-name>
config/<mod-id>/client/world/local/<world>/<file-name>
config/<mod-id>/client/world/server/<server>/<file-name>
```

Values are resolved in this order:

1. defaults declared in code;
2. the distributable default file;
3. the active world or server file.

Missing entries inherit from the previous layer.

### Server

Server schemas also have a distributable default and an authoritative file for
the active world:

```text
config/<mod-id>/server/world/default/<file-name>
<world>/serverconfig/<mod-id>/<file-name>
```

The server applies code defaults, then the distributable default, then the
world file. Connected clients never read these files.

MezzConfig owns every backing file. Read and update settings through
`IConfigValue` instead of editing files in mod code.

## Server-authoritative values

`SERVER` and `CLIENT_PER_WORLD` solve different problems:

- a client-per-world schema stores a separate client preference for each world
  or server;
- a server schema stores the authoritative setting with the world and sends its
  effective value to connected clients.

When a client receives a server snapshot, the schema becomes active in memory.
It remains read-only because the local process does not own the server's file.
Only effective values are synchronized; a restart-required value that is saved
but not yet effective remains pending on the server.

Remote editing is intentionally outside the MezzConfig API. A mod that permits
clients to request server changes must define its own authorization and request
protocol, then apply accepted changes to the authoritative server schema.

The synchronization channel is optional. A client can connect to a server that
does not provide it; in that case, the declared server schema remains on its
defaults instead of receiving a remote snapshot.

## Categories and localization

Storage categories determine the sections written to the config file:

```java
IConfigCategoryBuilder general = schema.addCategory("general");
IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
	.build();
```

Categories and values stay in declaration order. Their names are stable storage
identifiers, not display text.

The localization path passed to the schema builder forms the translation keys.
For a path of `example_mod.config.client`, the example above uses:

```text
example_mod.config.client.general
example_mod.config.client.general.description
example_mod.config.client.general.enabled
example_mod.config.client.general.enabled.description
```

MezzConfig uses these translations in generated file comments. Config editors
can use the same keys for names and descriptions.

Editor-only categories can organize a screen differently without changing file
storage:

```java
IConfigEditorCategoryBuilder quick = schema.addEditorCategory("quick");
IConfigEditorCategoryBuilder advanced = schema.addEditorCategory("advanced");

general.addBoolean("enabled", true)
	.addEditorCategory(quick)
	.addEditorCategory(advanced)
	.build();
```

A value may appear in several editor categories. If none are assigned, an
editor can show it in its storage category.

## Edit modes and restart requirements

Value builders can describe how an editor should present an update:

```java
IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
	.setEditMode(ConfigValueEditMode.IMMEDIATE)
	.build();

IConfigValue<Integer> cacheSize = general.addInteger(
	"cacheSize",
	256,
	16,
	4096
)
	.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
	.build();
```

Edit modes are presentation hints; MezzConfig does not provide GUI widgets or
screen staging. Restart requirements affect runtime values:

- `getPendingValue()` returns the saved selection;
- `getValue()` returns the value currently in effect.

Without a restart requirement, the two values change together. Setting a
restart-required value back to its effective value cancels the pending change.

## Atomic updates

Use a batch when related settings must not expose a partially updated state:

```java
List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(
	updater -> {
		updater.set(enabled, false);
		updater.set(mode, Mode.ADVANCED);
	}
);
```

MezzConfig validates the complete batch before changing anything. If one value
is invalid or the callback throws, none of the updates are applied.

## Listeners

Choose listeners based on which state matters:

- `addListener` observes one effective value;
- `addPendingListener` observes one saved value, including a change waiting for
  restart;
- value-scoped batch listeners observe a complete batch when that value
  participates;
- schema batch listeners observe every non-empty batch in the schema.

```java
Runnable unsubscribe = schema.addBatchListener(changes -> {
	rebuildDerivedState();
});
```

Notifications are synchronous and run after the complete state is committed.
Keep the returned removal callbacks for teardown. See the Javadocs when code
depends on exact notification order or reentrant updates.

## Discovery and identity

`Configs.getSchemas()` returns an unmodifiable snapshot of schemas that have
been built and registered. Editor integrations can inspect their categories and
values without linking to implementation classes.

Use these fields as a stable address:

```text
mod id + schema type + schema id
```

Treat the schema id as opaque. Do not infer current paths or registration order
from it.

## File reload and recovery

Active local files are watched for external changes. Valid entries elsewhere in
a damaged file remain usable; missing entries inherit their lower-layer or code
default. When MezzConfig can safely repair invalid stored content, it preserves
a numbered backup before writing the canonical form.

Ordinary filesystem failures are treated as potentially temporary. MezzConfig
does not replace or delete a file merely because it cannot currently be opened.
Initial file access happens during `build()` and can fail synchronously.

Built schemas, values, and sorting configs are thread-safe. Builders are not.
Concurrent operations are valid but unordered, and listeners run on the thread
that applies the change.
