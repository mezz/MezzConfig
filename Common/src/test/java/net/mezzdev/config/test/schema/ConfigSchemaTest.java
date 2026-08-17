package net.mezzdev.config.test.schema;

import net.mezzdev.config.api.schema.IConfigBatchUpdater;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.value.ConfigListOrdering;
import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigKeyValueSerializer;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.PackedColor;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigEditorCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigValueData;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSchemaTest {
	@Test
	public void addEnumListSupportsEmptyDefaultLists() {
		// Setup: an enum list can have no default entries, so the enum class must provide the element type.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		// Operation: create an empty default list and deserialize stored enum names later.
		ConfigValue<List<TestMode>> modes = builder.addEnumList(
				"modes",
				List.of(),
				TestMode.class
			)
			.build();

		// Assertions: the empty default is valid, and the serializer still knows which enum values to parse.
		assertEquals(List.of(), modes.getDefaultValue());
		assertEquals(
			List.of(TestMode.STANDARD, TestMode.ADVANCED),
			modes.getSerializer()
				.deserialize("[STANDARD, ADVANCED]")
				.getResult()
				.orElseThrow()
		);
	}

	@Test
	public void addListWrapsElementSerializer() {
		// Setup: custom list values are built from a normal element serializer.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		// Operation: create a boolean list with the public addList helper.
		ConfigValue<List<Boolean>> flags = builder.addList(
				"flags",
				List.of(true),
				BooleanSerializer.INSTANCE
			)
			.build();

		// Assertions: the list serializer can still parse the whole list from storage text.
		assertEquals(
			List.of(false, true),
			flags.getSerializer()
				.deserialize("false, true")
				.getResult()
				.orElseThrow()
		);

		// Assertions: integrations can discover the original element serializer for per-element list editing.
		assertTrue(flags.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<?> listSerializer = (IConfigListValueSerializer<?>) flags.getSerializer();
		assertSame(BooleanSerializer.INSTANCE, listSerializer.getElementSerializer());
	}

	@Test
	public void builtInListsSnapshotDefaultsAndUpdates() {
		// Setup: retain mutable aliases for both a default list and a later update.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		List<String> defaultValues = new ArrayList<>(List.of("default"));
		var valueBuilder = builder.addStringList("names", defaultValues);
		defaultValues.add("late default mutation");
		ConfigValue<List<String>> names = valueBuilder.build();
		AtomicReference<IAppliedConfigValueChange<List<String>>> appliedChange = new AtomicReference<>();
		names.addListener(appliedChange::set);
		List<String> updatedValues = new ArrayList<>(List.of("updated"));

		// Operation: set the mutable list, then mutate the caller-owned alias after set returns.
		assertTrue(names.set(updatedValues));
		updatedValues.add("late update mutation");

		// Assertions: defaults, current values, and change records are stable unmodifiable snapshots.
		assertEquals(List.of("default"), names.getDefaultValue());
		assertEquals(List.of("updated"), names.getValue());
		assertEquals(List.of("default"), appliedChange.get().oldValue());
		assertEquals(List.of("updated"), appliedChange.get().newValue());
		assertThrows(UnsupportedOperationException.class, () -> names.getDefaultValue().add("mutation"));
		assertThrows(UnsupportedOperationException.class, () -> names.getValue().add("mutation"));
		assertThrows(UnsupportedOperationException.class, () -> appliedChange.get().newValue().add("mutation"));
	}

	@Test
	public void builtInListsAreUnmodifiableAfterLoading(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[category]",
			"names = first, second"
		));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<List<String>> names = builder.addStringList("names", List.of("default"))
			.build();
		createSchema(path, builder);

		List<String> loadedNames = names.getValue();

		assertEquals(List.of("first", "second"), loadedNames);
		assertThrows(UnsupportedOperationException.class, () -> loadedNames.add("mutation"));
	}

	@Test
	public void customValueTypesUseThePublicSerializerExtensionPath() {
		// Setup: define a key-value entry type and serializer outside MezzConfig's built-in types.
		IConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ExtensionEntrySerializer serializer = new ExtensionEntrySerializer();
		ExtensionEntry defaultValue = new ExtensionEntry("defaultKey", "defaultValue");

		// Operation: create scalar and ordered list values through the public methods available to extensions.
		IConfigValue<ExtensionEntry> value = builder.addValue("extensionValue", defaultValue, serializer)
			.build();
		IConfigValue<List<ExtensionEntry>> values = builder.addKeyValueList(
				"extensionValues",
				List.of(defaultValue),
				serializer,
				ConfigListOrdering.UNORDERED
			)
			.build();

		// Assertions: the custom entry round-trips and remains discoverable inside the list serializer.
		assertSame(serializer, value.getSerializer());
		assertEquals(
			new ExtensionEntry("updatedKey", "updatedValue"),
			value.getSerializer().deserialize("updatedKey=updatedValue").getResult().orElseThrow()
		);
		assertEquals(
			List.of(new ExtensionEntry("first", "one"), new ExtensionEntry("second", "two")),
			values.getSerializer().deserialize("first=one, second=two").getResult().orElseThrow()
		);
		assertTrue(values.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<?> listSerializer = (IConfigListValueSerializer<?>) values.getSerializer();
		assertEquals(ConfigListOrdering.UNORDERED, listSerializer.getOrdering());
		assertListElementSerializer(values, "element=value", new ExtensionEntry("element", "value"));
	}

	@Test
	public void addBuiltInValueHelpersCreateSerializers() {
		// Setup: create one value for each built-in helper that the public category builder offers.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<String> name = builder.addString("name", "default")
			.build();
		ConfigValue<List<String>> names = builder.addStringList("names", List.of("default"))
			.build();
		ConfigValue<List<Boolean>> flags = builder.addBooleanList("flags", List.of(true))
			.build();
		ConfigValue<Integer> unboundedInteger = builder.addInteger("unboundedInteger", 1)
			.build();
		ConfigValue<List<Integer>> unboundedIntegers = builder.addIntegerList("unboundedIntegers", List.of(1))
			.build();
		ConfigValue<List<Integer>> boundedIntegers = builder.addIntegerList("boundedIntegers", List.of(1), 0, 10)
			.build();
		ConfigValue<PackedColor> color = builder.addColor("color", PackedColor.argb(0xFF112233))
			.build();
		ConfigValue<List<PackedColor>> colors = builder.addColorList("colors", List.of(PackedColor.argb(0xFF112233)))
			.build();
		ConfigValue<Long> boundedLong = builder.addLong("boundedLong", 1L, 0L, 10L)
			.build();
		ConfigValue<List<Long>> unboundedLongs = builder.addLongList("unboundedLongs", List.of(1L))
			.build();
		ConfigValue<List<Long>> boundedLongs = builder.addLongList("boundedLongs", List.of(1L), 0L, 10L)
			.build();
		ConfigValue<Double> boundedDouble = builder.addDouble("boundedDouble", 1.5, 0.0, 10.0)
			.build();
		ConfigValue<List<Double>> unboundedDoubles = builder.addDoubleList("unboundedDoubles", List.of(1.5))
			.build();
		ConfigValue<List<Double>> boundedDoubles = builder.addDoubleList("boundedDoubles", List.of(1.5), 0.0, 10.0)
			.build();
		ConfigValue<TestMode> restrictedEnum = builder.addEnum("restrictedEnum", TestMode.STANDARD, List.of(TestMode.STANDARD))
			.build();
		ConfigValue<List<TestMode>> restrictedEnums = builder.addEnumList("restrictedEnums", List.of(), List.of(TestMode.STANDARD))
			.build();

		// Assertions: each helper wires a serializer that understands its public storage format.
		assertEquals("configured", name.getSerializer().deserialize("configured").getResult().orElseThrow());
		assertEquals(List.of("one", "two"), names.getSerializer().deserialize("one, two").getResult().orElseThrow());
		assertEquals(List.of(false, true), flags.getSerializer().deserialize("false, true").getResult().orElseThrow());
		assertEquals(Integer.MIN_VALUE, unboundedInteger.getSerializer().getRange().orElseThrow().min());
		assertEquals(List.of(1, 2), unboundedIntegers.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(List.of(1, 2), boundedIntegers.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertTrue(colors.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<?> colorsSerializer = (IConfigListValueSerializer<?>) colors.getSerializer();
		assertEquals(PackedColor.rgb(0x112233), colorsSerializer.getElementSerializer().deserialize("0x112233").getResult().orElseThrow());
		assertEquals("0xFF112233", color.getSerializer().serialize(PackedColor.argb(0xFF112233)));
		assertEquals(PackedColor.argb(0xFF445566), color.getSerializer().deserialize("0xFF445566").getResult().orElseThrow());
		assertEquals(
			List.of(PackedColor.rgb(0x112233), PackedColor.argb(0x80445566)),
			colors.getSerializer().deserialize("0x112233, 0x80445566").getResult().orElseThrow()
		);
		assertEquals(10L, boundedLong.getSerializer().getRange().orElseThrow().max());
		assertEquals(List.of(1L, 2L), unboundedLongs.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(List.of(1L, 2L), boundedLongs.getSerializer().deserialize("1, 2").getResult().orElseThrow());
		assertEquals(10.0, boundedDouble.getSerializer().getRange().orElseThrow().max());
		assertEquals(List.of(1.5, 2.5), unboundedDoubles.getSerializer().deserialize("1.5, 2.5").getResult().orElseThrow());
		assertEquals(List.of(1.5, 2.5), boundedDoubles.getSerializer().deserialize("1.5, 2.5").getResult().orElseThrow());
		assertEquals(TestMode.STANDARD, restrictedEnum.getSerializer().deserialize("STANDARD").getResult().orElseThrow());
		assertEquals(List.of(TestMode.STANDARD), restrictedEnums.getSerializer().deserialize("STANDARD").getResult().orElseThrow());

		// Assertions: built-in list helpers expose element serializers for GUI integrations.
		assertListElementSerializer(names, "configured", "configured");
		assertListElementSerializer(flags, "false", false);
		assertListElementSerializer(unboundedIntegers, "2", 2);
		assertListElementSerializer(boundedIntegers, "2", 2);
		assertListElementSerializer(colors, "0xFF445566", PackedColor.argb(0xFF445566));
		assertListElementSerializer(unboundedLongs, "2", 2L);
		assertListElementSerializer(boundedLongs, "2", 2L);
		assertListElementSerializer(unboundedDoubles, "2.5", 2.5);
		assertListElementSerializer(boundedDoubles, "2.5", 2.5);
		assertListElementSerializer(restrictedEnums, "STANDARD", TestMode.STANDARD);

		// Assertions: bounded and restricted helpers reject values outside their declared valid range.
		assertTrue(boundedIntegers.getSerializer().deserialize("11").getDiagnostics().getFirst().contains("Invalid integer"));
		assertTrue(color.getSerializer().deserialize("112233").getDiagnostics().getFirst().contains("Invalid color"));
		assertTrue(boundedLongs.getSerializer().deserialize("11").getDiagnostics().getFirst().contains("Invalid long"));
		assertTrue(boundedDoubles.getSerializer().deserialize("11.0").getDiagnostics().getFirst().contains("Invalid double"));
		assertTrue(restrictedEnum.getSerializer().deserialize("ADVANCED").getDiagnostics().getFirst().contains("Invalid enum name"));
		assertTrue(restrictedEnums.getSerializer().deserialize("ADVANCED").getDiagnostics().getFirst().contains("Invalid enum name"));
	}

	@Test
	public void addCategoryRejectsInvalidNames() {
		assertThrows(IllegalArgumentException.class, () -> new ConfigCategoryBuilder("mezz_config.config.test", "bad-name"));
	}

	@Test
	public void addValueRejectsDuplicateNames() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", false)
			.build();

		assertThrows(IllegalArgumentException.class, () -> builder.addBoolean("enabled", true));
	}

	@Test
	public void addValueRejectsDuplicateLegacyNames() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", false)
			.addLegacyName("oldEnabled");

		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyName("enabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyName("oldEnabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValue("category", "oldEnabled"));
	}

	@Test
	public void addValueRejectsCurrentOrDuplicateLegacyValueReferences() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", false)
			.addLegacyValue("legacy", "enabled");

		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValue("category", "enabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValueMigration("category", "enabled", Boolean::parseBoolean));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValue("legacy", "enabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValueMigration("legacy", "enabled", Boolean::parseBoolean));
	}

	@Test
	public void addValueRejectsDuplicateLegacyValueMigrations() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", false)
			.addLegacyValueMigration("legacy", "enabled", Boolean::parseBoolean);

		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValue("legacy", "enabled"));
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addLegacyValueMigration("legacy", "enabled", Boolean::parseBoolean));
	}

	@Test
	public void configValueBuilderDefaultsToBatchEditingWithStorageCategory() {
		// Setup: no editor hints are declared for this value.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		// Operation: build the value with only its mandatory storage information.
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();

		// Assertions: editors can batch by default and use the value's storage category when no editor category is set.
		assertEquals(ConfigValueEditMode.BATCH, enabled.getEditMode());
		assertEquals(ConfigValueRestartRequirement.NONE, enabled.getRestartRequirement());
		assertEquals(List.of(), enabled.getEditorCategories());
	}

	@Test
	public void configValueBuilderResolvesEditorOnlyCategoriesInSchemaOrder() {
		// Setup: values can declare when editors should save them, when changes take effect, and where editors should show them.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigEditorCategoryBuilder quick = new ConfigEditorCategoryBuilder("mezz_config.config.test", "quick");
		ConfigEditorCategoryBuilder advanced = new ConfigEditorCategoryBuilder("mezz_config.config.test", "advanced");

		// Operation: declare presentation hints before the schema builds the final category instances.
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.setEditMode(ConfigValueEditMode.IMMEDIATE)
			.addEditorCategory(advanced)
			.addEditorCategory(quick)
			.build();
		ConfigValue<Boolean> requiresRestart = builder.addBoolean("requiresRestart", false)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = createSchema(
			List.of(builder),
			List.of(builder, quick, advanced)
		);

		// Assertions: editor-only categories are not storage categories, but they are exposed in schema editor order.
		assertEquals(List.of("category"), getCategoryNames(schema.getCategories()));
		assertEquals(List.of("category", "quick", "advanced"), getCategoryNames(schema.getEditorCategories()));
		assertEquals(ConfigValueEditMode.IMMEDIATE, enabled.getEditMode());
		assertEquals(ConfigValueRestartRequirement.NONE, enabled.getRestartRequirement());
		assertEquals(List.of("quick", "advanced"), getCategoryNames(enabled.getEditorCategories()));
		assertSame(schema.getEditorCategories().get(1), enabled.getEditorCategories().get(0));
		assertSame(schema.getEditorCategories().get(2), enabled.getEditorCategories().get(1));
		assertEquals(ConfigValueEditMode.BATCH, requiresRestart.getEditMode());
		assertEquals(ConfigValueRestartRequirement.GAME_RESTART, requiresRestart.getRestartRequirement());
	}

	@Test
	public void editorOnlyCategoriesAreNotSerialized(@TempDir Path tempDir) throws IOException {
		// Setup: a value is stored in one category but shown under a separate editor-only category.
		Path path = tempDir.resolve("test.ini");
		ConfigCategoryBuilder storage = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigEditorCategoryBuilder editorOnly = new ConfigEditorCategoryBuilder("mezz_config.config.test", "ingredientSorting");
		storage.addBoolean("sortIngredientsAfterLookup", true)
			.addEditorCategory(editorOnly)
			.build();
		ConfigSchema schema = createSchema(
			path,
			List.of(storage),
			List.of(storage, editorOnly)
		);

		// Operation: save the storage schema to disk.
		ConfigSerializer.save(path, schema.getCategories());

		// Assertions: the editor-only category is available for GUI ordering but not written as an empty file section.
		List<String> lines = Files.readAllLines(path);
		assertEquals(List.of("general", "ingredientSorting"), getCategoryNames(schema.getEditorCategories()));
		assertTrue(lines.contains("[general]"));
		assertFalse(lines.contains("[ingredientSorting]"));
	}

	@Test
	public void schemaExposesOwningModIdForGuiDiscovery() {
		// Setup: a schema is created for a specific mod id.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", true)
			.build();

		// Operation: build the schema with owner metadata.
		ConfigSchema schema = new ConfigSchema(
			"example_mod",
			Path.of("test.ini"),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);

		// Assertions: GUI integrations can discover which mod owns this schema from the schema itself.
		assertEquals("example_mod", schema.getModId());
	}

	@Test
	public void configValueBuilderRejectsDuplicateEditorCategories() {
		// Setup: a value builder already has one editor category.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		var valueBuilder = builder.addBoolean("enabled", true)
			.addEditorCategory(builder);

		// Operation and assertions: duplicate editor categories are rejected early instead of producing duplicate GUI rows.
		assertThrows(IllegalArgumentException.class, () -> valueBuilder.addEditorCategory(builder));
	}

	@Test
	public void batchUpdaterNotifiesListenersAfterAllValuesUpdate() {
		// Setup: two values share a schema, and listeners observe both single-value and batch notifications.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> valueChanges = new ArrayList<>();
		List<String> valueBatches = new ArrayList<>();
		List<String> schemaBatches = new ArrayList<>();
		enabled.addListener(change -> valueChanges.add("%s -> %s, count = %s".formatted(change.oldValue(), change.newValue(), count.getValue())));
		enabled.addBatchListener(changes -> valueBatches.add("value batch: %s, count = %s".formatted(changes.size(), count.getValue())));
		schema.addListener(changes -> schemaBatches.add("schema batch: %s, enabled = %s, count = %s".formatted(
			changes.size(),
			enabled.getValue(),
			count.getValue()
		)));

		// Operation: queue both changes in one callback. Nothing should change until the callback returns.
		List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> {
			updater.set(enabled, false);
			updater.set(count, 3);
			assertTrue(enabled.getValue());
			assertEquals(1, count.getValue());
			assertEquals(List.of(), valueChanges);
			assertEquals(List.of(), valueBatches);
			assertEquals(List.of(), schemaBatches);
		});

		// Assertions: both values changed before any listener ran, and listeners receive one batch.
		assertEquals(2, changes.size());
		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertEquals(List.of("true -> false, count = 3"), valueChanges);
		assertEquals(List.of("value batch: 2, count = 3"), valueBatches);
		assertEquals(List.of("schema batch: 2, enabled = false, count = 3"), schemaBatches);
	}

	@Test
	public void batchUpdaterValidatesAllUpdatesBeforeChangingValues() {
		// Setup: one queued update is valid and one queued update is outside the integer range.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(builder);

		// Operation: try to apply the mixed-validity batch.
		assertThrows(IllegalArgumentException.class, () -> schema.batchUpdate(updater -> {
			updater.set(enabled, false);
			updater.set(count, 11);
		}));

		// Assertions: validation happens before mutation, so even the valid update is not applied.
		assertTrue(enabled.getValue());
		assertEquals(1, count.getValue());
	}

	@Test
	public void batchUpdaterUsesLastValueForRepeatedUpdates() {
		// Setup: one value is set twice in the same batch callback.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);

		// Operation: queue a no-op update first, then queue the final value that should be applied.
		List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> {
			updater.set(enabled, true);
			updater.set(enabled, false);
		});

		// Assertions: repeated updates are collapsed to the last queued value.
		assertEquals(1, changes.size());
		assertFalse(enabled.getValue());
		assertEquals(true, changes.getFirst().oldValue());
		assertEquals(false, changes.getFirst().newValue());
	}

	@Test
	public void batchUpdaterSnapshotsListValuesWhenQueued() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<List<String>> names = builder.addStringList("names", List.of("default"))
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> updatedNames = new ArrayList<>(List.of("queued"));

		schema.batchUpdate(updater -> {
			updater.set(names, updatedNames);
			updatedNames.add("mutated before apply");
		});

		assertEquals(List.of("queued"), names.getValue());
	}

	@Test
	public void batchUpdaterRejectsUpdatesAfterCallbackReturns() {
		// Setup: capture the callback-scoped updater so the test can try to misuse it later.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);
		AtomicReference<IConfigBatchUpdater> retainedUpdater = new AtomicReference<>();

		// Operation: run an empty batch but retain the updater reference.
		List<? extends IAppliedConfigValueChange<?>> changes = schema.batchUpdate(updater -> retainedUpdater.set(updater));

		// Assertions: the updater cannot be used after the schema-owned callback has returned.
		assertEquals(List.of(), changes);
		assertThrows(IllegalStateException.class, () -> retainedUpdater.get().set(enabled, false));
		assertTrue(enabled.getValue());
	}

	@Test
	public void setNotifiesSchemaBatchListeners() {
		// Setup: a single config value still uses schema batch listeners when set directly.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> schemaBatches = new ArrayList<>();
		schema.addListener(changes -> {
			IAppliedConfigValueChange<?> change = changes.getFirst();
			schemaBatches.add("%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()));
		});

		// Operation: set the current value again, then apply one change through the normal IConfigValue API.
		assertFalse(enabled.set(true));
		assertEquals(List.of(), schemaBatches);
		assertTrue(enabled.set(false));

		// Assertions: unchanged values return false without notification; a change is represented as a one-value batch.
		assertEquals(List.of("enabled: true -> false"), schemaBatches);
	}

	@Test
	public void addListenerReturnsUnsubscribeCallback() {
		// Setup: register a schema-wide batch listener and keep its unsubscribe callback.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);
		AtomicInteger notifications = new AtomicInteger();
		Runnable unsubscribe = schema.addListener(ignored -> notifications.incrementAndGet());

		// Operation: notify once, unsubscribe, then change the value again.
		assertTrue(enabled.set(false));
		unsubscribe.run();
		assertTrue(enabled.set(true));

		// Assertions: the schema listener only receives changes before its unsubscribe callback is run.
		assertEquals(1, notifications.get());
	}

	@Test
	public void schemaListenerCanUnsubscribeDuringNotification() {
		// Setup: register a schema-wide listener that removes itself while handling its first batch.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(builder);
		AtomicInteger notifications = new AtomicInteger();
		AtomicReference<Runnable> unsubscribe = new AtomicReference<>();
		unsubscribe.set(schema.addListener(ignored -> {
			notifications.incrementAndGet();
			unsubscribe.get().run();
		}));

		// Operation: apply two changes that would both notify if the listener remained subscribed.
		assertTrue(enabled.set(false));
		assertTrue(enabled.set(true));

		// Assertions: notification uses a stable listener snapshot and the self-unsubscribe prevents later callbacks.
		assertEquals(1, notifications.get());
	}

	@Test
	public void schemaListenerFailuresAreIsolatedAfterPersistenceIsScheduled(@TempDir Path tempDir) {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		AtomicInteger scheduledSaves = new AtomicInteger();
		ConfigSchema schema = new ConfigSchema(
			tempDir.resolve("test.ini"),
			List.of(builder),
			(command, delay) -> {
				scheduledSaves.incrementAndGet();
				return new CompletableFuture<>();
			}
		);
		AtomicInteger laterNotifications = new AtomicInteger();
		schema.addListener(ignored -> {
			assertEquals(1, scheduledSaves.get());
			throw new IllegalStateException("expected schema listener test failure");
		});
		schema.addListener(ignored -> laterNotifications.incrementAndGet());

		assertDoesNotThrow(() -> assertTrue(enabled.set(false)));

		assertEquals(1, scheduledSaves.get());
		assertEquals(1, laterNotifications.get());
	}

	@Test
	public void loadIfNeededNotifiesSchemaBatchListenersAfterAllValuesUpdate(@TempDir Path tempDir) throws IOException {
		// Setup: a config file changes two values before the schema is loaded.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[category]",
			"enabled = false",
			"count = 3"
		));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(path, builder);
		List<String> schemaBatches = new ArrayList<>();
		schema.addListener(changes -> schemaBatches.add(formatBatch(changes, enabled.getValue(), count.getValue())));

		// Operation: load the file through the schema.
		schema.loadIfNeeded();

		// Assertions: file loading also notifies after all changed values have been applied.
		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertEquals(List.of("enabled: true -> false, count: 1 -> 3; enabled = false; count = 3"), schemaBatches);
	}

	@Test
	public void gameRestartValuePersistsPendingSelectionUntilNextSchemaLoad(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[category]",
			"enabled = false"
		));
		Deque<Runnable> scheduledTasks = new ArrayDeque<>();
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = new ConfigSchema(
			path,
			List.of(builder),
			(command, delay) -> {
				scheduledTasks.add(command);
				return CompletableFuture.completedFuture(null);
			}
		);
		assertFalse(enabled.getValue());
		assertFalse(enabled.getPendingValue());
		AtomicInteger notifications = new AtomicInteger();
		enabled.addListener(ignored -> notifications.incrementAndGet());
		assertTrue(enabled.set(true));

		assertFalse(enabled.getValue());
		assertTrue(enabled.getPendingValue());
		assertEquals(0, notifications.get());
		runScheduledTasks(scheduledTasks);
		assertTrue(Files.readString(path).contains("enabled = true"));

		ConfigCategoryBuilder nextBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> nextEnabled = nextBuilder.addBoolean("enabled", true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		createSchema(path, nextBuilder);

		assertTrue(nextEnabled.getValue());
		assertTrue(nextEnabled.getPendingValue());
	}

	@Test
	public void worldRestartPromotesOnlyWorldRestartValues() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> worldValue = builder.addBoolean("worldValue", true)
			.setRestartRequirement(ConfigValueRestartRequirement.WORLD_RESTART)
			.build();
		ConfigValue<Boolean> gameValue = builder.addBoolean("gameValue", true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> changes = new ArrayList<>();
		schema.addListener(applied -> changes.add(formatChanges(applied)));

		assertTrue(worldValue.getValue());
		assertTrue(worldValue.set(false));
		assertTrue(gameValue.set(false));
		schema.promotePendingValuesAfterWorldRestart();

		assertFalse(worldValue.getValue());
		assertFalse(worldValue.getPendingValue());
		assertTrue(gameValue.getValue());
		assertFalse(gameValue.getPendingValue());
		assertEquals(List.of("worldValue: true -> false"), changes);
	}

	@Test
	public void mixedBatchNotifiesOnlyValuesThatBecomeEffective() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> immediate = builder.addBoolean("immediate", true)
			.build();
		ConfigValue<Boolean> afterRestart = builder.addBoolean("afterRestart", false)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = createSchema(builder);
		List<String> listenerChanges = new ArrayList<>();
		schema.addListener(changes -> listenerChanges.add(formatChanges(changes)));

		List<? extends IAppliedConfigValueChange<?>> savedChanges = schema.batchUpdate(updater -> {
			updater.set(immediate, false);
			updater.set(afterRestart, true);
		});

		assertEquals(2, savedChanges.size());
		assertFalse(immediate.getValue());
		assertFalse(afterRestart.getValue());
		assertTrue(afterRestart.getPendingValue());
		assertEquals(List.of("immediate: true -> false"), listenerChanges);
	}

	@Test
	public void layeredSchemaLoadsPlayerValuesOverPackDefaults(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("test.ini");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("test.ini");
		Files.write(defaultPath, List.of(
			"[category]",
			"enabled = false",
			"count = 2"
		));
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of(
			"[category]",
			"count = 3"
		));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigSchema schema = createSchema(
			new LayeredConfigSchemaPathResolver(
				defaultPath,
				new StaticConfigSchemaPathResolver(playerPath)
			),
			builder
		);

		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertEquals(Optional.of(playerPath), schema.getPath());
	}

	@Test
	public void layeredSchemaCreatesDefaultAndWritesChangesOnlyForPlayer(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("test.ini");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("test.ini");
		Deque<Runnable> scheduledTasks = new ArrayDeque<>();
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = new ConfigSchema(
			new LayeredConfigSchemaPathResolver(
				defaultPath,
				new StaticConfigSchemaPathResolver(playerPath)
			),
			List.of(builder),
			List.of(builder),
			(command, delay) -> {
				scheduledTasks.add(command);
				return CompletableFuture.completedFuture(null);
			}
		);

		schema.register(null, false);
		runScheduledTasks(scheduledTasks);

		assertTrue(Files.readString(defaultPath).contains("enabled = true"));
		assertFalse(Files.exists(playerPath));

		assertTrue(enabled.set(false));
		runScheduledTasks(scheduledTasks);

		assertTrue(Files.readString(defaultPath).contains("enabled = true"));
		assertTrue(Files.readString(playerPath).contains("enabled = false"));
	}

	@Test
	public void contextSchemaIsInactiveUntilPathResolves(@TempDir Path tempDir) {
		// Setup: a context-specific schema has no active backing file until the game context can resolve one.
		Path activePath = tempDir.resolve("world").resolve("local").resolve("test").resolve("test.ini");
		AtomicReference<Optional<Path>> resolvedPath = new AtomicReference<>(Optional.empty());
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(createPathResolver(resolvedPath), builder);

		// Assertions: inactive schemas keep their defaults and cannot be updated because there is nowhere to save them.
		assertEquals(Optional.empty(), schema.getPath());
		assertTrue(enabled.getValue());
		assertThrows(IllegalStateException.class, () -> enabled.set(false));

		// Operation: the client enters a world and the schema can now resolve its active path.
		resolvedPath.set(Optional.of(activePath));

		// Assertions: the schema is now editable and reports the active backing file.
		assertEquals(Optional.of(activePath), schema.getPath());
		assertTrue(enabled.set(false));
	}

	@Test
	public void serverSnapshotIsAuthoritativeAndDirectUpdatesAreRejected() {
		// Setup: a client has registered the shape of a server schema, but has no server values before synchronization.
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10)
			.build();
		ConfigValue<Boolean> afterRestart = builder.addBoolean("afterRestart", true)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		ConfigSchema schema = createRemoteServerSchema(builder);

		assertEquals(ConfigOwnership.SERVER, schema.getOwnership());
		assertEquals(ConfigScope.WORLD, schema.getScope());
		assertFalse(schema.isActive());
		assertFalse(schema.canEdit());

		// Operation: the server supplies the complete effective state and reports that this player is an operator.
		schema.applyRemoteSnapshot(List.of(
			new ServerConfigValueData("category", "enabled", "false"),
			new ServerConfigValueData("category", "count", "3"),
			new ServerConfigValueData("category", "afterRestart", "true", "false"),
			new ServerConfigValueData("newerServerCategory", "newerServerValue", "ignored")
		), true);

		// Assertions: reads use the synchronized snapshot, but synchronous setters cannot bypass server authority.
		assertTrue(schema.isActive());
		assertTrue(schema.canEdit());
		assertEquals(Optional.empty(), schema.getPath());
		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());
		assertTrue(afterRestart.getValue());
		assertFalse(afterRestart.getPendingValue());
		assertThrows(IllegalStateException.class, () -> enabled.set(true));
		assertFalse(enabled.getValue());

		// Operation: a malformed later snapshot is rejected as one batch.
		assertThrows(IllegalArgumentException.class, () -> schema.applyRemoteSnapshot(List.of(
			new ServerConfigValueData("category", "enabled", "true"),
			new ServerConfigValueData("category", "count", "outside-range")
		), true));

		// Assertions: no value from the malformed snapshot was applied.
		assertFalse(enabled.getValue());
		assertEquals(3, count.getValue());

		// Operation: an older server sends no value for a setting only this client knows.
		schema.applyRemoteSnapshot(List.of(
			new ServerConfigValueData("category", "enabled", "true")
		), true);

		// Assertions: known synchronized values apply and missing values safely use their declared defaults.
		assertTrue(enabled.getValue());
		assertEquals(1, count.getValue());
	}

	@Test
	public void serverUpdateRequestUsesServerAuthorizationWhenPermissionHintIsStale() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createRemoteServerSchema(builder);
		schema.applyRemoteSnapshot(List.of(
			new ServerConfigValueData("category", "enabled", "true")
		), false);
		List<Object> sentChunks = new ArrayList<>();
		ServerConfigNetworking.setClientSender(payload -> {
			sentChunks.add(payload);
			return true;
		});

		CompletableFuture<Void> result = schema.requestBatchUpdate(updater -> updater.set(enabled, false));

		assertFalse(result.isDone());
		assertFalse(sentChunks.isEmpty());
		ServerConfigRuntime.onClientDisconnect();
		assertTrue(result.isCompletedExceptionally());
		ServerConfigNetworking.setClientSender(payload -> false);
	}

	@Test
	public void serverSchemaCreatesAnAuthoritativeWorldFileWhenActivated(@TempDir Path tempDir) throws IOException {
		// Setup: the server schema has a pack default, but its world path is inactive before a world starts.
		Path defaultPath = tempDir.resolve("config").resolve("test_mod").resolve("server").resolve("default").resolve("server.ini");
		Path worldPath = tempDir.resolve("world").resolve("serverconfig").resolve("test_mod").resolve("server.ini");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		Deque<Runnable> scheduledTasks = new ArrayDeque<>();
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = new ConfigSchema(
			"test_mod",
			new LayeredConfigSchemaPathResolver(defaultPath, () -> activePath.get()),
			List.of(builder),
			List.of(builder),
			(command, delay) -> {
				scheduledTasks.add(command);
				return CompletableFuture.completedFuture(null);
			},
			ConfigOwnership.SERVER,
			ConfigScope.WORLD,
			new ServerConfigKey("test_mod", "server.ini")
		);
		schema.register(null, false);

		// Operation: starting a world activates its serverconfig path.
		activePath.set(Optional.of(worldPath));
		assertEquals(Optional.of(worldPath), schema.getPath());
		runScheduledTasks(scheduledTasks);

		// Assertions: both distributable defaults and the world's authoritative copy exist without an in-game edit.
		assertTrue(Files.readString(defaultPath).contains("enabled = true"));
		assertTrue(Files.readString(worldPath).contains("enabled = true"));
	}

	@Test
	public void contextSchemaLoadsFromNewPathAsOneBatch(@TempDir Path tempDir) throws IOException {
		// Setup: two different client-world paths have different saved values for the same schema.
		Path firstPath = tempDir.resolve("world").resolve("local").resolve("first").resolve("test.ini");
		Path secondPath = tempDir.resolve("world").resolve("local").resolve("second").resolve("test.ini");
		Files.createDirectories(firstPath.getParent());
		Files.createDirectories(secondPath.getParent());
		Files.write(firstPath, List.of(
			"[category]",
			"enabled = false"
		));
		Files.write(secondPath, List.of(
			"[category]",
			"enabled = true"
		));
		AtomicReference<Optional<Path>> resolvedPath = new AtomicReference<>(Optional.of(firstPath));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = createSchema(createPathResolver(resolvedPath), builder);
		List<String> valueChanges = new ArrayList<>();
		List<String> schemaBatches = new ArrayList<>();
		enabled.addListener(change -> valueChanges.add("%s -> %s".formatted(change.oldValue(), change.newValue())));
		schema.addListener(changes -> schemaBatches.add(formatChanges(changes)));

		// Operation: load the first world-specific config file.
		assertFalse(enabled.getValue());

		// Assertions: the value loads from the active file and listeners receive one applied batch.
		assertEquals(Optional.of(firstPath), schema.getPath());
		assertEquals(List.of("true -> false"), valueChanges);
		assertEquals(List.of("enabled: true -> false"), schemaBatches);

		// Operation: switch context and load the second world-specific config file.
		resolvedPath.set(Optional.of(secondPath));
		assertTrue(enabled.getValue());

		// Assertions: switching paths resets and loads before notifying, so listeners see old world value -> new world value.
		assertEquals(Optional.of(secondPath), schema.getPath());
		assertEquals(List.of("true -> false", "false -> true"), valueChanges);
		assertEquals(List.of("enabled: true -> false", "enabled: false -> true"), schemaBatches);
	}

	@Test
	public void contextSchemaFlushesPendingSaveBeforePathSwitch(@TempDir Path tempDir) throws IOException {
		// Setup: a client-world schema has an active path and a save scheduler that never runs delayed saves.
		Path firstPath = tempDir.resolve("world").resolve("local").resolve("first").resolve("test.ini");
		Path secondPath = tempDir.resolve("world").resolve("local").resolve("second").resolve("test.ini");
		AtomicReference<Optional<Path>> resolvedPath = new AtomicReference<>(Optional.of(firstPath));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true)
			.build();
		ConfigSchema schema = new ConfigSchema(
			createPathResolver(resolvedPath),
			List.of(builder),
			List.of(builder),
			(command, delay) -> new CompletableFuture<>()
		);

		// Operation: update the first world, then switch to the second world before the delayed save can run.
		assertTrue(enabled.set(false));
		resolvedPath.set(Optional.of(secondPath));
		assertTrue(enabled.getValue());

		// Assertions: switching paths flushes the first world's pending save before values reset for the second world.
		assertTrue(Files.readString(firstPath).contains("enabled = false"));
		assertEquals(Optional.of(secondPath), schema.getPath());
		assertTrue(enabled.getValue());
	}

	@Test
	public void buildCategoryRequiresValuesToBeBuilt() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		builder.addBoolean("enabled", true);

		assertThrows(IllegalStateException.class, () -> createSchema(builder));
	}

	@Test
	public void addIntegerRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addInteger("tooHigh", 11, 0, 10));
		assertThrows(IllegalArgumentException.class, () -> builder.addInteger("invalidRange", 1, 10, 0));
	}

	@Test
	public void addLongRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addLong("tooHigh", 11L, 0L, 10L));
		assertThrows(IllegalArgumentException.class, () -> builder.addLong("invalidRange", 1L, 10L, 0L));
	}

	@Test
	public void addDoubleRejectsInvalidDefaultsAndRanges() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("tooHigh", 11.0, 0.0, 10.0));
		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("invalidRange", 1.0, 10.0, 0.0));
		assertThrows(IllegalArgumentException.class, () -> builder.addDouble("infiniteDefault", Double.POSITIVE_INFINITY));
	}

	@Test
	public void addRestrictedEnumRejectsInvalidDefaultsAndValidValueLists() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");

		assertThrows(IllegalArgumentException.class, () -> builder.addEnum("invalidDefault", TestMode.ADVANCED, List.of(TestMode.STANDARD)));
		assertThrows(IllegalArgumentException.class, () -> builder.addEnum("emptyValidValues", TestMode.STANDARD, List.of()));
		assertThrows(IllegalArgumentException.class, () -> builder.addEnumList("emptyListValidValues", List.<TestMode>of(), List.of()));
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}

	private record ExtensionEntry(String key, String value) {}

	private static final class ExtensionEntrySerializer implements IConfigKeyValueSerializer<ExtensionEntry, String, String> {
		@Override
		public IConfigValueSerializer<String> getKeySerializer() {
			return StringSerializer.INSTANCE;
		}

		@Override
		public IConfigValueSerializer<String> getValueSerializer() {
			return StringSerializer.INSTANCE;
		}

		@Override
		public String getKey(ExtensionEntry entry) {
			return entry.key();
		}

		@Override
		public String getValue(ExtensionEntry entry) {
			return entry.value();
		}

		@Override
		public ExtensionEntry createEntry(String key, String value) {
			return new ExtensionEntry(key, value);
		}

		@Override
		public String serialize(ExtensionEntry value) {
			return "%s=%s".formatted(value.key(), value.value());
		}

		@Override
		public IDeserializeResult<ExtensionEntry> deserialize(String string) {
			String[] parts = string.split("=", 2);
			if (parts.length != 2) {
				return IDeserializeResult.failure("Extension entries must contain a key and value separated by '='");
			}
			return IDeserializeResult.success(new ExtensionEntry(parts[0], parts[1]));
		}

		@Override
		public boolean isValid(ExtensionEntry value) {
			return !value.key().isBlank() && !value.value().isBlank();
		}

		@Override
		public String getValidValuesDescription() {
			return "A non-blank key and value separated by '='";
		}
	}

	private static ConfigSchema createSchema(ConfigCategoryBuilder... builders) {
		return createSchema(Path.of("test.ini"), builders);
	}

	private static ConfigSchema createSchema(Path path, ConfigCategoryBuilder... builders) {
		return createSchema(path, List.of(builders), List.of(builders));
	}

	private static ConfigSchema createSchema(
		List<ConfigCategoryBuilder> builders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders
	) {
		return createSchema(Path.of("test.ini"), builders, editorCategoryBuilders);
	}

	private static ConfigSchema createSchema(
		Path path,
		List<ConfigCategoryBuilder> builders,
		List<ConfigEditorCategoryBuilder> editorCategoryBuilders
	) {
		return new ConfigSchema(
			path,
			builders,
			editorCategoryBuilders,
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}

	private static ConfigSchema createSchema(ConfigSchemaPathResolver pathResolver, ConfigCategoryBuilder... builders) {
		return new ConfigSchema(
			pathResolver,
			List.of(builders),
			List.of(builders),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}

	private static ConfigSchema createRemoteServerSchema(ConfigCategoryBuilder... builders) {
		return new ConfigSchema(
			"test_mod",
			() -> Optional.empty(),
			List.of(builders),
			List.of(builders),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigOwnership.SERVER,
			ConfigScope.WORLD,
			new ServerConfigKey("test_mod", "server.ini")
		);
	}

	private static ConfigSchemaPathResolver createPathResolver(AtomicReference<Optional<Path>> resolvedPath) {
		return new ConfigSchemaPathResolver() {
			@Override
			public Optional<Path> resolvePath() {
				return resolvedPath.get();
			}
		};
	}

	private static void runScheduledTasks(Deque<Runnable> scheduledTasks) {
		int attempts = 0;
		while (!scheduledTasks.isEmpty() && attempts++ < 100) {
			scheduledTasks.removeFirst()
				.run();
		}
		assertTrue(scheduledTasks.isEmpty(), "Scheduled config saves did not settle.");
	}

	private static List<String> getCategoryNames(List<? extends IConfigEditorCategory> categories) {
		return categories.stream()
			.map(IConfigEditorCategory::getName)
			.toList();
	}

	@SuppressWarnings("unchecked")
	private static <T> void assertListElementSerializer(
		IConfigValue<List<T>> configValue,
		String serializedValue,
		T expectedValue
	) {
		assertTrue(configValue.getSerializer() instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<T> listSerializer = (IConfigListValueSerializer<T>) configValue.getSerializer();
		IDeserializeResult<T> result = listSerializer.getElementSerializer()
			.deserialize(serializedValue);

		assertEquals(List.of(), result.getDiagnostics());
		assertEquals(expectedValue, result.getResult().orElseThrow());
	}

	private static String formatBatch(
		List<? extends IAppliedConfigValueChange<?>> changes,
		boolean enabled,
		int count
	) {
		String formattedChanges = String.join(", ", changes.stream()
			.map(change -> "%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()))
			.toList());
		return "%s; enabled = %s; count = %s".formatted(
			formattedChanges,
			enabled,
			count
		);
	}

	private static String formatChanges(List<? extends IAppliedConfigValueChange<?>> changes) {
		return String.join(", ", changes.stream()
			.map(change -> "%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()))
			.toList());
	}
}
