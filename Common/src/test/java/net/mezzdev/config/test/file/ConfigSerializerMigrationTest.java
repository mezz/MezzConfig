package net.mezzdev.config.test.file;

import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.value.ConfigValueMigration;
import net.mezzdev.config.value.ConfigValueReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSerializerMigrationTest {
	@Test
	public void loadMigratesLegacyCategoryName(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"enabled = false"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general")
			.addLegacyName("legacy");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.build();
		ConfigCategory category = categoryBuilder.build(null);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
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
		ConfigCategory category = categoryBuilder.build(null);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
	}

	@Test
	public void loadMigratesCurrentValueNameWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"enabled = yes"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", false)
			.addLegacyValueMigration(legacyValue -> "yes".equalsIgnoreCase(legacyValue))
			.build();
		ConfigCategory category = categoryBuilder.build(null);

		ConfigSerializer.load(path, List.of(category));

		assertTrue(enabled.getValue());
	}

	@Test
	public void loadMigratesLegacyCategoryAndValueNamesWithLegacyValueMigration(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[legacy]",
			"disabled = true"
		));
		ConfigCategoryBuilder categoryBuilder = new ConfigCategoryBuilder("mezz_config.config.test", "general")
			.addLegacyName("legacy");
		ConfigValue<Boolean> enabled = categoryBuilder.addBoolean("enabled", true)
			.addLegacyName("disabled")
			.addLegacyValueMigration(legacyValue -> !Boolean.parseBoolean(legacyValue))
			.build();
		ConfigCategory category = categoryBuilder.build(null);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
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
		ConfigValueMigration<Boolean> migration = ConfigValueMigration.migrate(value, Boolean::parseBoolean);
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(value),
			Map.of(legacyValue, List.of(migration))
		);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(value.getValue());
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
		ConfigValueMigration<Boolean> firstMigration = ConfigValueMigration.migrate(first, Boolean::parseBoolean);
		ConfigValueMigration<Boolean> secondMigration = ConfigValueMigration.migrate(second, oldValue -> !Boolean.parseBoolean(oldValue));
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(first, second),
			Map.of(legacyValue, List.of(firstMigration, secondMigration))
		);

		ConfigSerializer.load(path, List.of(category));

		assertTrue(first.getValue());
		assertFalse(second.getValue());
	}
}
