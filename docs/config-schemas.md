# Config schemas

A schema groups settings that belong to the same place: this Minecraft
installation, one world or server, or the world itself.

[Back to the API guide](API.md)

## Choose who owns the settings

Start by deciding where a setting belongs. Settings with different owners
should use different schemas, even when they are part of the same feature.

| The setting should... | Create it with | Example |
| --- | --- | --- |
| Follow this Minecraft installation everywhere. | `createClientSchemaBuilder` | UI scale, overlay position, display preferences. |
| Be different for each local world or multiplayer server. | `createClientPerWorldSchemaBuilder` | A client-side overlay or filter configured for one world. |
| Be controlled by the world and shared with every player. | `createServerSchemaBuilder` | Gameplay limits, feature switches, or balance rules. |

This ownership is the main reason to use a schema. MezzConfig selects the
appropriate file and context, switches per-world client values with the active
connection, and sends server-owned values to connected clients. Your feature
code can read the same typed value without managing those transitions itself.

Declare schemas from shared/common mod initialization. This ensures that a
dedicated server knows about world settings and a client knows how to receive
those settings when it connects.

## Declare a schema

Create one registration for your mod, add categories and values, then build the
schema:

```java
IConfigRegistration configs = Configs.forMod("example_mod");

IConfigSchemaBuilder builder = configs.createClientSchemaBuilder("client.ini", "example_mod.config.client");

IConfigCategoryBuilder general = builder.addCategory("general");
IConfigValue<Boolean> enabled = general.addBoolean("enabled", true).build();
IConfigValue<Integer> maxEntries = general.addInteger("maxEntries", 16, 1, 128).build();

IConfigSchema clientConfig = builder.build();
```

Keep the returned `IConfigValue` objects; they are how the rest of the mod reads
and changes settings. Build all values before building the schema.

The built-in helpers cover booleans, strings, bounded numbers, colors, enums,
and typed lists. See [Custom config values](custom-values.md) only when a setting
has a mod-specific value type.

## Use settings in the mod

Read the effective value wherever the feature needs it:

```java
if (enabled.get()) {
	showOverlay(maxEntries.get());
}
```

Use a listener when an already-running feature must react immediately:

```java
maxEntries.addListener(change -> {
	resizeOverlay(change.newValue());
});
```

Config values and their usual listeners both live for the lifetime of the mod,
so most mods do not need to keep the returned removal callback. This also applies
to client-per-world values: the same listener remains registered and is notified
when joining or leaving a world changes the effective value, including the
transition back to declared defaults outside a world.

Keep and call the removal callback only when the listener captures something
shorter-lived than the config value, such as a config screen, reloadable runtime,
or connection-specific component. Removing it when that object is torn down
prevents stale callbacks and keeps the listener from retaining the old object.

To apply an edit from your own config screen, call `set`:

```java
maxEntries.set(32);
```

MezzConfig validates and saves the edit. A remote client cannot directly change
a server-owned setting; if your mod supports that, send a request through your
own permission-checked network protocol and apply the accepted change on the
server.

## Understand when values are available

- A client schema is available while the game client is running.
- A client-per-world schema is active while a local world or multiplayer
  connection is open, and changes automatically with that context.
- A server schema is active on the server for the loaded world. Connected
  clients receive its effective values as a read-only copy.

Use `schema.isActive()` when code can run outside the schema's context. Inactive
schemas continue to return their declared defaults.

When the connected server does not have MezzConfig, no server-owned values are
sent. Those schemas remain inactive on the client and return their declared
defaults, so client features can continue without server support.

For restart-required settings, `get()` remains the value currently in use
and `getEditorInfo().getPendingValue()` is the value saved for the next restart.
This lets a config screen show the pending selection without making running code
behave as if the restart already happened.

## Know where settings are stored

MezzConfig uses conventional locations automatically:

| Schema | Storage |
| --- | --- |
| `CLIENT` | Pack default: `config/<mod-id>/client/default/<file-name>`<br>User: `config/<mod-id>/client/<file-name>` |
| `CLIENT_PER_WORLD` | Pack default: `config/<mod-id>/client/world/default/<file-name>`<br>Singleplayer: `config/<mod-id>/client/world/local/<world-folder>/<file-name>`<br>Multiplayer: `config/<mod-id>/client/world/server/<server-id>/<file-name>` |
| `SERVER` | Pack default: `config/<mod-id>/server/world/default/<file-name>`<br>World: `<world>/serverconfig/<mod-id>/<file-name>` |

Conventional schemas resolve values from the code default, then the pack
default, then the user or active-context file. This lets modpacks ship defaults
while worlds and players override only the settings they need.

When connecting to a server that has MezzConfig, the server sends a stable ID
stored with its world. Client-per-world settings use that ID, so they keep
working when the server's address or name changes. If the server does not
support MezzConfig (i.e. a vanilla server) the client falls back to the
server-list name and address. Connecting never requires server support.

Use the `createClientSchemaBuilder(Path, String)` overload only when integrating
with an existing client file location. It uses that one file without a separate
pack default.
Mod code should read and update values through `IConfigValue`, not access schema
files directly.

## Make the schema useful to config screens

MezzConfig describes configs but does not render a screen. Config-screen
integrations can discover built schemas, categories, value types, bounds,
restart requirements, and translations.

Use `value.getEditorInfo()` when an integration needs a value's stable name,
localization key, presentation metadata, serializer, or pending-value listener
hooks. Regular feature code can stay on `IConfigValue` and its value accessors.

The localization prefix and stable category/value names form translation keys.
For example, `assets/example_mod/lang/en_us.json` can contain:

```json
{
	"example_mod.config.client.general": "General",
	"example_mod.config.client.general.description": "General display settings.",
	"example_mod.config.client.general.enabled": "Enabled",
	"example_mod.config.client.general.enabled.description": "Show the overlay.",
	"example_mod.config.client.general.maxEntries": "Maximum entries",
	"example_mod.config.client.general.maxEntries.description": "The most entries the overlay can show."
}
```

In a development run, MezzConfig also warns about missing keys once game
translations have loaded. The warning includes a complete JSON object with
blank values; copy its entries into the appropriate language file and fill in
the text.

Storage categories become sections in the file. If a screen needs a different
layout, add editor categories and assign values to them without changing the
file. Do this while declaring values, before `builder.build()`:

```java
IConfigEditorCategoryBuilder quick = builder.addEditorCategory("quick");

IConfigValue<Boolean> showStatus = general.addBoolean("showStatus", true)
	.addEditorCategory(quick)
	.build();
```

Set a restart requirement on values that cannot safely change while the game is
running. Build schemas before registering a generated config screen so the
integration can discover them.

## Change related settings together

Use a batch when several edits represent one action and feature code must never
observe only part of it:

```java
clientConfig.batchUpdate(update -> {
	update.set(enabled, false);
	update.set(maxEntries, 32);
});
```

MezzConfig validates the whole batch before applying it. A schema batch listener
is useful when derived state depends on several settings:

```java
clientConfig.addBatchListener(changes -> {
	rebuildOverlay();
});
```

For renamed values, moved files, or older config formats, see
[Migrations](migrations.md). Use the published Javadocs when code depends on
exact lifecycle, listener, or failure behavior.
