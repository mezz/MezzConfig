# Migrate existing settings

Use a migration when a config value, config file, or saved sort order has moved
or changed format. A migration is a one-time import: MezzConfig considers it
only when the destination file does not exist. An existing destination always
wins.

[Back to the API guide](API.md)

## Choose the migration API

| Existing data | Change | API |
| --- | --- | --- |
| MezzConfig file | Rename a value in the same category | `IConfigValueBuilder.addLegacyName` |
| MezzConfig file | Move or rename a value | `IConfigValueBuilder.addLegacyValue` |
| MezzConfig file | Move a value and change its type or format | `IConfigValueBuilder.addLegacyValueMigration` |
| MezzConfig file | Move or rename the file | `IConfigSchemaBuilder.setLegacySources` |
| Another format | Import values into a MezzConfig schema | `IConfigSchemaBuilder.setLegacyMigration` |
| Saved sort order | Import an old order | `ISortingConfig.setLegacyMigration` |

The value-level methods apply only while `setLegacySources` imports an older
MezzConfig file. They do not modify a destination file that already exists.

## Import an older MezzConfig file

Declare the current schema, add legacy mappings where storage changed, and
then list the old file locations:

```java
IConfigSchemaBuilder schema = configs.createClientSchemaBuilder(
	"client.ini",
	"example_mod.config.client"
);

IConfigCategoryBuilder general = schema.addCategory("general");
IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
	.addLegacyName("enableIntegration")
	.build();

IConfigCategoryBuilder search = schema.addCategory("search");
IConfigValue<String> filter = search.addString("filter", "")
	.addLegacyValue("general", "ingredientFilter")
	.build();

schema.setLegacySources(List.of(
	oldConfigDirectory.resolve("example-client.ini"),
	olderConfigDirectory.resolve("example.cfg")
));

IConfigSchema clientConfig = schema.build();
```

MezzConfig checks the paths in order and imports the first file that exists.
Values whose storage names did not change are matched automatically. In the
example, `addLegacyName` handles a rename within one category, while
`addLegacyValue` handles a move from another category.

Use `addLegacyValueMigration` when the old value also needs conversion:

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

The old category or value name must differ from the current storage location.
If only the serialized format changed, give the current value a new storage
name and migrate from the old one. When a source contains both a current key
and a legacy key for the same value, the current key wins.

## Import another config format

Use `setLegacyMigration` when the old file was not written by MezzConfig. The
migrator reads the old format and supplies typed values from the destination
schema:

```java
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
		switch (stored.trim()) {
			case "true" -> migration.set(enabled, true);
			case "false" -> migration.set(enabled, false);
			default -> migration.rejectValue(
				"Invalid legacy enabled value: " + stored
			);
		}
	}
});
```

Call `rejectValue` when one legacy value is invalid but other values can still
be imported. Throw an exception when the file as a whole cannot be read or
interpreted. MezzConfig validates all supplied values together, backs up the
selected source, and writes the destination.

If the migration supplies any usable update, MezzConfig writes it and keeps the
defaults for rejected settings. If it reports rejections without any usable
update, migration fails and does not create the destination.

The same migrator can call `setSortedValues(...)` to import a persistent sort
order along with the schema values.

## Handle the result

Implement `IConfigMigrator` as a class when the mod needs to override
`onMigrationComplete` and report the outcome:

```java
@Override
public void onMigrationComplete(IConfigMigrationResult result) {
	if (!result.getDiagnostics().isEmpty()) {
		LOGGER.warn("Config migration {}: {} imported, {} rejected: {}",
			result.getStatus(),
			result.getImportedValueCount(),
			result.getRejectedValueCount(),
			result.getDiagnostics());
	}

	if (result.getStatus() == ConfigMigrationStatus.FAILED) {
		result.getFailure().ifPresent(error ->
			LOGGER.error("Could not migrate the old config", error)
		);
	}
}
```

| Status | Meaning |
| --- | --- |
| `MIGRATED` | The destination was created from a legacy source. |
| `SKIPPED_DESTINATION_EXISTS` | The destination already existed. |
| `SKIPPED_NO_LEGACY_FILE` | None of the candidate source files existed. |
| `SKIPPED_INACTIVE` | The destination is unavailable in this environment. |
| `FAILED` | Nothing was imported and the destination was not created. |

The result also provides imported config value and rejected value counts,
diagnostics, the selected source and destination paths, and the source backup
path. The completion callback runs once after migration reaches one of these
outcomes.

To retry after a failure, fix the legacy source and restart. If a destination
already exists, preserve it if needed, delete it, and restart.

## When migration runs

Migration normally runs when the schema is built. World-scoped client and
server schemas wait until their first local world destination becomes active.
A remote server config does not trigger local migration. A client-only schema
on a dedicated server reports `SKIPPED_INACTIVE`.

Each migration is considered once per schema instance. Visiting another world
does not run it again.

For a standalone persistent order, see
[Sorting configs](sorting.md#migrate-an-old-order).
