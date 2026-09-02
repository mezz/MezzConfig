package net.mezzdev.config.test.file;

import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSerializerMigrationTest {
	@Test
	public void loadMigratesLegacyValueFromOldCategory(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.get());
	}

	@Test
	public void loadMigratesLegacyValueName(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.get());
	}

	@Test
	public void loadMigratesLosslessArrayThroughPublicListSerializer(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertEquals(List.of("first", "a,b", ""), names.get());
	}

	@Test
	public void loadMigratesLegacyValueNameWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
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
		ConfigSerializer.load(path, List.of(category));

		// Assertions: the legacy-name migration converts the old serialized text into the current value.
		assertTrue(enabled.get());
	}

	@Test
	public void loadMigratesLegacyCategoryAndValueNamesWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.get());
	}

	@Test
	public void loadMigratesStructuredLegacyListAsTypedValues(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertEquals("1:2:3", numbers.get());
	}

	@Test
	public void loadDoesNotMigrateWholeLegacyCategory(@TempDir Path tempDir) throws IOException {
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
		ConfigSerializer.load(path, List.of(category));

		// Assertions: only the explicitly declared legacy value migrates.
		assertFalse(enabled.get());
		assertTrue(visible.get());
	}

	@Test
	public void loadMigratesLegacyValueFromUnknownCategory(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

		assertFalse(value.get());
	}

	@Test
	public void loadMigratesLegacyValueToMultipleCurrentValues(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.load(path, List.of(category));

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
}
