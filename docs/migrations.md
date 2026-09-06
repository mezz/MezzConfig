# Migrate existing settings

MezzConfig has different migration tools for storage names, file locations, and
foreign file formats. Choose the narrowest tool that matches the change.

[Back to the API guide](API.md)

## Choose a migration

| Change | API |
| --- | --- |
| Rename a value in the same category | `IConfigValueBuilder.addLegacyName` |
| Move or rename a value inside a MezzConfig schema | `IConfigValueBuilder.addLegacyValue` |
| Move a value and change its type or serialized form | `IConfigValueBuilder.addLegacyValueMigration` |
| Move an entire MezzConfig file | `IConfigSchemaBuilder.setLegacySources` |
| Import a file that was not written by MezzConfig | `IConfigSchemaBuilder.setLegacyMigration` |
| Import an old persistent sort order | `ISortingConfig.setLegacyMigration` |

File-level migrations run only when the new destination does not already exist.
This keeps an old file from overwriting settings that have already been saved in
the new location. Value-level migrations follow the same rule within a file:
the current storage key always wins, regardless of file order, and a legacy
value is used only when that key is absent.

## Rename a value

Use `addLegacyName` when only the value's storage name changed:

```java
IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
	.addLegacyName("enableIntegration")
	.build();
```

MezzConfig loads `[general].enableIntegration` into the new
`[general].enabled` value and writes the current name when the file is
canonicalized.

## Move a value

Use `addLegacyValue` when the old value was in another category, had another
name, or both:

```java
IConfigValue<String> filter = search.addString("filter", "")
	.addLegacyValue("general", "ingredientFilter")
	.build();
```

The current serializer must still understand the old stored form.

## Change a value's type or format

When the old form needs conversion, provide its serializer and a typed
conversion function:

```java
IConfigValue<Duration> timeout = general.addValue(
	"timeout",
	Duration.ofSeconds(30),
	durationSerializer
)
	.addLegacyValueMigration(
		"general",
		"timeoutSeconds",
		legacySecondsSerializer,
		seconds -> Duration.ofSeconds(seconds)
	)
	.build();
```

The migration source must use an old category, an old value name, or both. If a
format changes under the same storage name, either make the current serializer
accept both forms or move to a new storage name and migrate from the old one.

## Move a MezzConfig file

Use `setLegacySources` when the old file was also written by MezzConfig:

```java
IConfigSchemaBuilder schema = configs.createClientSchemaBuilder(
	"client.ini",
	"example_mod.config.client"
);

// Declare and build the destination values first.
IConfigValue<Boolean> enabled = schema.addCategory("general")
	.addBoolean("enabled", true)
	.build();

schema.setLegacySources(List.of(
	oldConfigDirectory.resolve("example-client.ini"),
	olderConfigDirectory.resolve("example.cfg")
));

IConfigSchema clientConfig = schema.build();
```

Paths are checked in order. MezzConfig loads the first existing source with the
current schema, including value-level legacy names and conversions. It preserves
and backs up the source, then writes the imported values to the new location.

## Import a foreign config format

Use `setLegacyMigration` when the old file was not written by MezzConfig. The
callback parses only the old format and supplies typed destination values:

```java
IConfigSchemaBuilder schema = configs.createClientSchemaBuilder(
	"client.ini",
	"example_mod.config.client"
);
IConfigValue<Boolean> enabled = schema.addCategory("general")
	.addBoolean("enabled", true)
	.build();

schema.setLegacyMigration(List.of(oldPropertiesFile), (path, migration) -> {
	Properties properties = new Properties();
	try (Reader reader = Files.newBufferedReader(path)) {
		properties.load(reader);
	}

	String stored = properties.getProperty("enabled");
	if (stored != null) {
		boolean migratedEnabled = switch (stored.trim()) {
			case "true" -> true;
			case "false" -> false;
			default -> throw new IllegalArgumentException(
				"Invalid legacy enabled value: " + stored
			);
		};
		migration.set(enabled, migratedEnabled);
	}
});

IConfigSchema clientConfig = schema.build();
```

MezzConfig validates all queued values before applying any of them. It handles
backup creation and writing the current format atomically; the callback does
not need to understand MezzConfig's file format.

`IConfigMigrationContext` can also update a persistent sorting config as part
of the same import with `setSortedValues(...)`.

## Observe the result

Implement `IConfigMigrator` as a class when the mod needs the final outcome:

```java
@Override
public void onMigrationComplete(IConfigMigrationResult result) {
	if (result.getStatus() == ConfigMigrationStatus.FAILED) {
		result.getFailure().ifPresent(error ->
			LOGGER.error("Could not migrate the old config", error)
		);
	}
}
```

The result distinguishes a successful migration, a destination that already
exists, a missing legacy source, an inactive destination, and a failure. It can
also expose the selected source, destination, and preserved backup paths.

The completion callback runs once after MezzConfig reaches a final outcome. An
exception from the callback is logged but does not change that outcome.

## Migration guarantees

- Candidate paths are normalized and checked in declaration order.
- A file-level migration never replaces an existing destination.
- Typed updates are validated as one transaction.
- A failed migration does not partially update the destination schema.
- A selected legacy file is preserved and backed up before replacement work.

For persistent runtime orders, see [Sorting configs](sorting.md#migrate-an-old-order).
