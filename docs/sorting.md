# Sorting configs

A sorting config remembers how a player wants to order a list whose entries are
discovered while the game is running.

[Back to the API guide](API.md)

## When a normal config list is not enough

Use a normal schema value when every possible entry is known in advance. Use a
sorting config when other mods, plugins, or registries determine which entries
exist.

[Just Enough Items (JEI)](https://github.com/mezz/JustEnoughItems) is a useful
example. Its ingredient list discovers mod names and ingredient types from the
installed modpack, and its recipe categories come from registered content. JEI
can start with familiar defaults—Minecraft, item stacks, and crafting first—yet
still preserve a player's preferred order when mods are added or removed.

MezzConfig handles the same moving-list problem: entries that still exist keep
their saved position, removed entries are ignored, and new entries are placed
using your default comparator.

## Create the order

Create a sorting config once during common initialization. Use stable IDs rather
than translated display names so language changes do not lose the saved order:

```java
IConfigRegistration configs = Configs.forMod("example_mod");

ISortingConfig<String> categoryOrder = configs.createSortingConfig(
	"recipe-category-order.txt",
	Comparator
		.comparing((String id) -> !id.equals("minecraft:crafting"))
		.thenComparing(String.CASE_INSENSITIVE_ORDER),
	false
);
```

The comparator defines the order a new player sees and where newly discovered
entries go. This example puts the familiar crafting category first, then sorts
the rest by ID.

The final argument controls whether the player may hide entries:

- `false` keeps every available entry visible and only saves their order;
- `true` lets an editor hide an entry by leaving it out of the saved visible
  list.

MezzConfig stores the preference in your mod's client config directory.

## Connect it to a screen

Whenever the screen is built or refreshed, pass every category currently
available:

```java
List<String> allCategoryIds = discoverRecipeCategoryIds();
List<String> displayedCategoryIds = categoryOrder.getSortedValues(
	allCategoryIds
);
```

Render `displayedCategoryIds` in the returned order. After the player drags rows
into a new order, save what the screen displays:

```java
categoryOrder.setSortedValues(
	allCategoryIds,
	reorderedCategoryIds
);
```

Always pass the complete current collection as the first argument. The second
list is the player's visible order. With removal disabled, accidentally omitted
entries remain visible and are appended in their default order.

If another API expects a comparator instead of a sorted list, pass it
`categoryOrder.getComparator(allCategoryIds)`.

## Refresh when the order changes

Register a listener when another screen or command can update the order while a
view is open:

```java
Runnable removeListener = categoryOrder.addChangeListener(() -> {
	rebuildCategoryList();
});
```

Most mod-lifetime listeners can remain registered and ignore the returned
removal callback. This example is a special case because the listener captures a
shorter-lived view: keep and call the removal callback when the view closes so
the sorting config does not retain or notify the old view.

## Sort something other than strings

For a custom ID type, use the `createSortingConfig` overload that accepts an
`IConfigValueSerializer<T>`. The serialized form becomes the saved identity, so
choose a stable domain value such as a resource ID—not a mutable object or
localized name.

See [Custom config values](custom-values.md) for serializer guidance. If the mod
already has an order file to preserve, register `setLegacyMigration` immediately
after creating the sorting config; see [Migrations](migrations.md) for the
broader migration workflow.
