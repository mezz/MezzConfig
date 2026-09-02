# MezzConfig API guide

This guide gets a mod from a MezzConfig dependency to a working typed config.
It covers Minecraft 1.21.1 on Fabric, Forge, and NeoForge.

Start by adding the dependency for your loader from the
[project README](../README.md#add-mezzconfig-to-your-mod). This guide uses only
the stable API under `net.mezzdev.config.api`.

## Create a config

Declare configs during common mod initialization. The same declaration code can
run on a physical client or a dedicated server.

```java
import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.IConfigValue;

public final class ExampleConfig {
	public static final IConfigValue<Boolean> ENABLED;
	public static final IConfigValue<Integer> MAX_ENTRIES;
	public static final IConfigSchema CLIENT;

	static {
		IConfigRegistration registration = Configs.forMod("example_mod");
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder(
			"client.ini",
			"example_mod.config.client"
		);
		IConfigCategoryBuilder general = schema.addCategory("general");

		ENABLED = general.addBoolean("enabled", true)
			.build();
		MAX_ENTRIES = general.addInteger("maxEntries", 16, 1, 128)
			.build();

		CLIENT = schema.build();
	}

	private ExampleConfig() {}
}
```

The declaration has four levels:

```text
registration for one mod
└── schema stored and activated as one unit
    └── category stored as one section
        └── typed config value
```

Build every value, then build the schema once. Building an active local schema
resolves its starting values before returning, so they are ready to read.

## Read and update values

Keep the `IConfigValue` objects returned by the builders. They are the normal
runtime interface; mods should not read or write MezzConfig files directly.

```java
if (ExampleConfig.ENABLED.get()) {
	startIntegration();
}

ExampleConfig.MAX_ENTRIES.set(32);
```

`set` validates and saves the value. It returns `true` when the saved value
changed and `false` when the same valid value was already saved.

Listen for effective-value changes when runtime behavior must refresh:

```java
ExampleConfig.ENABLED.addListener(change -> {
	boolean enabled = change.newValue();
	updateIntegration(enabled);
});
```

Most listeners live as long as their config values and can remain registered for
the lifetime of the mod, so their returned removal callbacks can be ignored.
Keep and run a removal callback when a listener captures a shorter-lived object,
such as a screen, reloadable runtime, or connection-specific component. This
prevents callbacks to torn-down objects and allows those objects to be collected.
Listeners run synchronously on the thread applying the change.

## Choose the schema owner

The builder factory determines who owns a config and when it is active.

| Factory | Use it for |
| --- | --- |
| `createClientSchemaBuilder` | Client preferences shared across worlds and servers. |
| `createClientPerWorldSchemaBuilder` | Client preferences that vary by singleplayer world or multiplayer server. |
| `createServerSchemaBuilder` | World-owned settings controlled by the server and synchronized to clients. |
| `createClientSchemaBuilderAtLocation` | A client config stored at one complete, explicit path. |

Client schemas are safe to declare from common initialization code. On a
dedicated server they remain inactive and default-backed, without touching a
client file. Server schemas should also be declared on both physical sides: the
server owns the file, while connected clients receive an in-memory read-only
snapshot.

See [Config schemas](config-schemas.md) for file locations, activation states,
synchronization, restart-required values, atomic batches, and config-editor
metadata.

## Built-in value types

`IConfigCategoryBuilder` includes helpers for:

- booleans and strings;
- integers, longs, and finite doubles, with optional bounds;
- RGB and ARGB packed colors;
- enums, including restricted sets of valid constants;
- typed lists of every built-in type.

Most configs should use these helpers. For a mod-specific immutable type, see
[Custom values](custom-values.md).

## Discover schemas

Config-screen and diagnostic integrations can discover built, registered
schemas and inspect whether each one is active:

```java
for (IConfigSchema schema : Configs.getSchemas()) {
	String owner = schema.getModId();
	String id = schema.getId();
	boolean active = schema.isActive();
}
```

Treat `IConfigSchema.getId()` as an opaque storage identity. Use the schema's
mod id, type, and id together when an integration needs a stable address.

Build schemas before config-screen registration so integrations can discover
them during their setup phase.

## Where to go next

- [Config schemas](config-schemas.md) explains ownership, storage, lifecycle,
  updates, listeners, and editor presentation.
- [Custom values](custom-values.md) explains serializer contracts and structured
  list values.
- [Migrations](migrations.md) shows how to preserve settings across names,
  locations, and formats.
- [Sorting configs](sorting.md) covers persistent ordering for runtime-discovered
  values.

## Supported API boundary

The supported API is the non-internal surface under
`net.mezzdev.config.api`, published in the `config-api` artifact. Types marked
`@ApiStatus.Internal`, public implementation classes in `Common`, and loader
implementation classes are not stable API.

Use these guides for workflow and examples. Use the Javadocs published with the
API artifact for complete method contracts, validation rules, return values,
exceptions, and threading details.
