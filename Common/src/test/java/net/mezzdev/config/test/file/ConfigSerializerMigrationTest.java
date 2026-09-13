package net.mezzdev.config.test.file;

import net.mezzdev.config.api.value.change.IAppliedConfigValueChange;
import net.mezzdev.config.file.ConfigFileReader;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.value.AppliedConfigValueChange;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import net.mezzdev.config.value.ConfigValueUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSerializerMigrationTest {
	@Test
	public void parseMigrationUpdatesMigratesLegacyValueFromOldCategory(@TempDir Path tempDir) throws IOException {
		// Setup: a value declares its former category while keeping the same storage name.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"enabled = false"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.addLegacyValue("legacy", "enabled")
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: parse and apply migration updates from the legacy category.
		migrate(path, List.of(category));

		// Assertions: the old category value becomes the current effective value.
		assertFalse(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesLegacyValueName(@TempDir Path tempDir) throws IOException {
		// Setup: a value declares a former storage name in its current category.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldEnabled = false"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.addLegacyName("oldEnabled")
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: parse and apply migration updates from the legacy name.
		migrate(path, List.of(category));

		// Assertions: the renamed value becomes the current effective value.
		assertFalse(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesCurrentValueTakesPrecedenceOverEarlierLegacyName(@TempDir Path tempDir) throws IOException {
		// Setup: a legacy declaration appears before a valid current declaration for the same value.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldEnabled = false",
			"enabled = true"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", false)
			.addLegacyName("oldEnabled")
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: parse and apply the migration-aware file.
		migrate(path, List.of(category));

		// Assertions: the explicit current name wins regardless of its later position.
		assertTrue(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesCurrentValueTakesPrecedenceOverLaterLegacyName(@TempDir Path tempDir) throws IOException {
		// Setup: a valid current declaration appears before a conflicting legacy declaration.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"enabled = true",
			"oldEnabled = false"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", false)
			.addLegacyName("oldEnabled")
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: parse and apply the migration-aware file.
		migrate(path, List.of(category));

		// Assertions: the explicit current name wins regardless of its earlier position.
		assertTrue(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void loadDoesNotRunLegacyConverterForCurrentConfig(@TempDir Path tempDir) throws IOException {
		// Setup: a normal current-file load sees only a legacy name with an observable converter.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldEnabled = yes"
		));
		AtomicInteger migrationCount = new AtomicInteger();
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", false)
			.addLegacyValueMigration(
				"general",
				"oldEnabled",
				StringSerializer.INSTANCE,
				legacyValue -> {
					migrationCount.incrementAndGet();
					return true;
				}
			)
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: load through the current config path rather than the migration path.
		ConfigSerializer.load(path, List.of(category));

		// Assertions: legacy-only data is ignored and its converter never runs.
		assertFalse(enabled.getEffectiveValueWithoutLoading());
		assertEquals(0, migrationCount.get());
	}

	@Test
	public void parseMigrationUpdatesMigratesLosslessArrayThroughPublicListSerializer(@TempDir Path tempDir) throws IOException {
		// Setup: a renamed string-list value contains comma-sensitive and empty elements.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldNames = [\"first\", \"a,b\", \"\"]"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<List<String>> names = categoryBuilder.addStringList("names", List.of())
			.addLegacyName("oldNames")
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: migrate through the public structured list serializer.
		migrate(path, List.of(category));

		// Assertions: all element boundaries and values survive migration.
		assertEquals(List.of("first", "a,b", ""), names.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesLegacyValueNameWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
		// Setup: the old value used a different storage name and different serialized text.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldEnabled = yes"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", false)
			.addLegacyValueMigration("general", "oldEnabled", StringSerializer.INSTANCE, "yes"::equalsIgnoreCase)
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: load a legacy-name config entry.
		migrate(path, List.of(category));

		// Assertions: the legacy-name migration converts the old serialized text into the current value.
		assertTrue(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesLegacyCategoryAndValueNamesWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
		// Setup: the old value used a former category, former name, and inverted boolean meaning.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"disabled = true"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.addLegacyValueMigration("legacy", "disabled", BooleanSerializer.INSTANCE, legacyValue -> !legacyValue)
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: migrate and convert the legacy boolean.
		migrate(path, List.of(category));

		// Assertions: category/name lookup and typed conversion produce the current value.
		assertFalse(enabled.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesStructuredLegacyListAsTypedValues(@TempDir Path tempDir) throws IOException {
		// Setup: a legacy structured integer list maps into a current scalar string.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"oldNumbers = [1, 2, 3]"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<String> numbers = categoryBuilder.addString("numbers", "")
			.addLegacyValueMigration(
				"general",
				"oldNumbers",
				new ListSerializer<>(new IntegerSerializer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
				values -> String.join(":", values.stream().map(Object::toString).toList())
			)
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: deserialize the legacy list by type and convert it into the current representation.
		migrate(path, List.of(category));

		// Assertions: typed elements reach the converter without losing their list structure.
		assertEquals("1:2:3", numbers.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesDoesNotMigrateWholeLegacyCategory(@TempDir Path tempDir) throws IOException {
		// Setup: only one value declares that it moved from the old category.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"enabled = false",
			"visible = false"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.addLegacyValue("legacy", "enabled")
			.build();
		ConfigValue<Boolean> visible = categoryBuilder.addBoolean("visible", true)
			.build();
		ConfigCategory category = buildCategory(path, categoryBuilder);

		// Operation: load a file where the old category contains another value with a matching current name.
		migrate(path, List.of(category));

		// Assertions: only the explicitly declared legacy value migrates.
		assertFalse(enabled.getEffectiveValueWithoutLoading());
		assertTrue(visible.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesLegacyValueFromUnknownCategory(@TempDir Path tempDir) throws IOException {
		// Setup: a manually assembled migration references a category absent from the current schema.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"value = false"
		));
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.current",
			"value",
			true,
			BooleanSerializer.INSTANCE
		);
		ConfigValueReference legacyValue = new ConfigValueReference("legacy", "value");
		ConfigValueMigration<Boolean> migration = ConfigValueMigration.migrate(
			value,
			BooleanSerializer.INSTANCE,
			oldValue -> oldValue
		);
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(value),
			Map.of(legacyValue, List.of(migration))
		);

		// Operation: parse and apply the manually mapped legacy value.
		migrate(path, List.of(category));

		// Assertions: migration lookup can read explicitly referenced unknown categories.
		assertFalse(value.getEffectiveValueWithoutLoading());
	}

	@Test
	public void parseMigrationUpdatesMigratesLegacyValueToMultipleCurrentValues(@TempDir Path tempDir) throws IOException {
		// Setup: one legacy boolean maps to two current values with different converters and listeners.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"value = true"
		));
		ConfigValue<Boolean> first = new ConfigValue<>(
			"mezz_config.config.test.current",
			"first",
			false,
			BooleanSerializer.INSTANCE
		);
		ConfigValue<Boolean> second = new ConfigValue<>(
			"mezz_config.config.test.current",
			"second",
			true,
			BooleanSerializer.INSTANCE
		);
		ConfigValueReference legacyValue = new ConfigValueReference("legacy", "value");
		ConfigValueMigration<Boolean> firstMigration = ConfigValueMigration.migrate(
			first,
			BooleanSerializer.INSTANCE,
			oldValue -> oldValue
		);
		ConfigValueMigration<Boolean> secondMigration = ConfigValueMigration.migrate(
			second,
			BooleanSerializer.INSTANCE,
			oldValue -> !oldValue
		);
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(first, second),
			Map.of(legacyValue, List.of(firstMigration, secondMigration))
		);
		List<String> regularChanges = new ArrayList<>();
		List<String> firstBatches = new ArrayList<>();
		List<String> secondBatches = new ArrayList<>();
		first.addListener(change -> regularChanges.add("%s -> %s, second = %s".formatted(change.oldValue(), change.newValue(), second.get())));
		first.addBatchListener(changes -> firstBatches.add(formatBatch(changes, first.get(), second.get())));
		second.addBatchListener(changes -> secondBatches.add(formatBatch(changes, first.get(), second.get())));

		// Operation: apply all updates derived from the shared legacy value.
		migrate(path, List.of(category));

		// Assertions: both conversions finish before listeners receive one complete applied batch.
		assertTrue(first.get());
		assertFalse(second.get());
		assertEquals(List.of("false -> true, second = false"), regularChanges);
		assertEquals(List.of("first: false -> true, second: true -> false; first = true; second = false"), firstBatches);
		assertEquals(List.of("first: false -> true, second: true -> false; first = true; second = false"), secondBatches);
	}

	private static String formatBatch(
		List<? extends IAppliedConfigValueChange<?>> changes,
		boolean first,
		boolean second
	) {
		String formattedChanges = String.join(", ", changes.stream()
			.map(change -> "%s: %s -> %s".formatted(
				change.configValue().getEditorInfo().getName(),
				change.oldValue(),
				change.newValue()
			))
			.toList());
		return "%s; first = %s; second = %s".formatted(
			formattedChanges,
			first,
			second
		);
	}

	private static ConfigCategory buildCategory(Path path, ConfigCategoryBuilder categoryBuilder) {
		ConfigSchema schema = new ConfigSchema(
			path,
			List.of(categoryBuilder),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
		return schema.getCategories()
			.getFirst();
	}

	private static void migrate(Path path, List<ConfigCategory> categories) throws IOException {
		try {
			List<AppliedConfigValueChange<?>> changes = new ArrayList<>();
			for (ConfigValueUpdate<?> update : ConfigSerializer.parseMigrationUpdates(path, categories)) {
				AppliedConfigValueChange<?> change = update.apply();
				if (change != null) {
					changes.add(change);
				}
			}
			ConfigValue.notifyPendingChangedValues(changes);
			ConfigValue.notifyChangedValues(changes);
		} catch (ConfigFileReader.MalformedFileException e) {
			throw new AssertionError("Test migration source must be a valid config file", e);
		}
	}
}
