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

IConfigSchemaBuilder builder = configs.createClientSchemaBuilder(
	"client.ini",
	"example_mod.config.client"
);
IConfigCategoryBuilder general = builder.addCategory("general");

IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
	.build();
IConfigValue<Integer> maxEntries = general.addInteger(
	"maxEntries",
	16,
	1,
	128
).build();

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
if (enabled.getValue()) {
	showOverlay(maxEntries.getValue());
}
```

Use a listener when an already-running feature must react immediately:

```java
Runnable removeListener = maxEntries.addListener(change -> {
	resizeOverlay(change.newValue());
});
```

Keep and call the returned removal callback when the feature is torn down.

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

For restart-required settings, `getValue()` remains the value currently in use
and `getPendingValue()` is the value saved for the next restart. This lets a
config screen show the pending selection without making running code behave as
if the restart already happened.

## Know where settings are stored

MezzConfig uses conventional locations automatically:

| Schema | Storage |
| --- | --- |
| `CLIENT` | `config/<mod-id>/client/<file-name>` |
| `CLIENT_PER_WORLD` | A distributable default plus a client file for the active local world or server. |
| `SERVER` | A distributable default plus `<world>/serverconfig/<mod-id>/<file-name>` for the authoritative world. |

Per-world and server schemas resolve values from the code default, then the
distributable default, then the active context file. This lets modpacks ship
defaults while worlds and players override only the settings they need.

Use `createClientSchemaBuilderAtLocation` only when integrating with an existing
client file location. Mod code should read and update values through
`IConfigValue`, not access schema files directly.

## Make the schema useful to config screens

MezzConfig describes configs but does not render a screen. Config-screen
integrations can discover built schemas, categories, value types, bounds,
restart requirements, and translations.

The localization prefix and stable category/value names form translation keys.
For the example above, provide:

```text
example_mod.config.client.general
example_mod.config.client.general.description
example_mod.config.client.general.enabled
example_mod.config.client.general.enabled.description
```

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
Runnable removeListener = clientConfig.addBatchListener(changes -> {
	rebuildOverlay();
});
```

For renamed values, moved files, or older config formats, see
[Migrations](migrations.md). Use the published Javadocs when code depends on
exact lifecycle, listener, or failure behavior.
