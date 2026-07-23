package net.mezzdev.config.file;

import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.value.ConfigValueUpdateType;
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
			BooleanSerializer.INSTANCE,
			ConfigValueUpdateType.IMMEDIATE
		);
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(value)
		);
		ConfigValueReference legacyValue = new ConfigValueReference("legacy", "value");
		ConfigValueMigration<Boolean, Boolean> migration = new ConfigValueMigration<>(
			value,
			BooleanSerializer.INSTANCE,
			oldValue -> oldValue
		);

		ConfigSerializer.load(path, List.of(category), Map.of(legacyValue, List.of(migration)));

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
			BooleanSerializer.INSTANCE,
			ConfigValueUpdateType.IMMEDIATE
		);
		ConfigValue<Boolean> second = new ConfigValue<>(
			"mezz_config.config.test.current",
			"second",
			true,
			BooleanSerializer.INSTANCE,
			ConfigValueUpdateType.IMMEDIATE
		);
		ConfigCategory category = new ConfigCategory(
			"mezz_config.config.test.current",
			"current",
			List.of(first, second)
		);
		ConfigValueReference legacyValue = new ConfigValueReference("legacy", "value");
		ConfigValueMigration<Boolean, Boolean> firstMigration = new ConfigValueMigration<>(
			first,
			BooleanSerializer.INSTANCE,
			oldValue -> oldValue
		);
		ConfigValueMigration<Boolean, Boolean> secondMigration = new ConfigValueMigration<>(
			second,
			BooleanSerializer.INSTANCE,
			oldValue -> !oldValue
		);

		ConfigSerializer.load(
			path,
			List.of(category),
			Map.of(legacyValue, List.of(firstMigration, secondMigration))
		);

		assertTrue(first.getValue());
		assertFalse(second.getValue());
	}
}
