# Sorting configs

A sorting config persists a user's preferred order for values that are
discovered at runtime. Use it for plugins, recipe types, registered content, or
any collection whose complete membership is not known while declaring a schema.

[Back to the API guide](API.md)

## Create a string order

Create sorting configs from the same `IConfigRegistration` used for schemas:

```java
IConfigRegistration configs = Configs.forMod("example_mod");

ISortingConfig<String> pluginOrder = configs.createSortingConfig(
	"plugin-order.txt",
	String.CASE_INSENSITIVE_ORDER,
	true
);
```

The last argument controls whether a user may hide values by removing them from
the saved visible order.

On a physical client, the order is stored at:

```text
config/<mod-id>/client/<file-name>
```

On a dedicated server it remains in memory and does not access a file.

Each sorting config and schema must resolve to a unique path. MezzConfig rejects
collisions before the conflicting config reads or writes the file.

## Apply the saved order

Pass the complete collection currently available whenever values need to be
displayed:

```java
List<String> allPlugins = discoverPlugins();
List<String> visiblePlugins = pluginOrder.getSortedValues(allPlugins);
```

The result is an unmodifiable, duplicate-free snapshot. MezzConfig reconciles
the saved preference with the supplied runtime values:

- saved values that still exist keep their preferred order;
- missing runtime values are ignored;
- newly discovered values are inserted with the default comparator;
- values explicitly hidden by the user remain hidden.

Use `getDefaultSortedValues(allValues)` to ignore the saved preference, or
`getComparator(allValues)` when another API needs a comparator.

## Save an edited order

Provide both the complete runtime set and the visible order selected by the
user:

```java
boolean changed = pluginOrder.setSortedValues(
	allPlugins,
	List.of("core", "compat", "debug")
);
```

The visible list must not contain duplicates or values outside `allPlugins`.
The method returns `true` when the saved order changed.

When removal is enabled, omitting a value that is present in `allPlugins` hides
it. Values that are discovered in a later call are visible by default. When
removal is disabled, omitted values are appended in default order instead.

Use `isVisible(allValues, value)` to query the reconciled visibility of one
value.

## Listen for changes

Register a listener when a view needs to refresh after the saved order changes:

```java
Runnable unsubscribe = pluginOrder.addChangeListener(() -> {
	rebuildPluginList();
});
```

Listeners run synchronously after the new order is active. Keep the returned
callback for teardown.

## Sort custom value types

The string overload is a convenience. For another effectively immutable type,
provide its config serializer and a default comparator:

```java
ISortingConfig<ResourceLocation> ingredientOrder =
	configs.createSortingConfig(
		"ingredient-order.txt",
		resourceLocationSerializer,
		Comparator.comparing(ResourceLocation::toString),
		true
	);
```

Custom sortable values must have stable equality and hash codes. Their
serializer must follow the normal [custom value contract](custom-values.md),
with one additional identity rule: equal values serialize identically, and
unequal values never share serialized text.

## Migrate an old order

Register a migration immediately after creating the sorting config, before any
method loads or changes its saved order:

```java
pluginOrder.setLegacyMigration(
	List.of(oldOrderFile),
	(path, migration) -> {
		List<String> savedOrder = Files.readAllLines(path);
		migration.setSortedValues(savedOrder, savedOrder);
	}
);
```

The first collection passed to `setSortedValues` is every value known to the
old order. The second is the visible subset in saved order. Supply hidden old
values only in the first collection.

Migration is attempted when the sorting config first needs its saved state and
only when the new destination does not exist. MezzConfig preserves and backs up
the selected source, validates the migrated order, and writes the current
format atomically.

Implement `ISortingConfigMigrator` as a class to receive an
`IConfigMigrationResult` after migration completes. The statuses and result
paths are the same as schema migration; see [Migrations](migrations.md).
