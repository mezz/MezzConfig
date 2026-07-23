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
		ConfigValueMigration<Boolean> migration = new ConfigValueMigration<>(
			BooleanSerializer.INSTANCE,
			value::set
		);

		ConfigSerializer.load(path, List.of(category), Map.of(legacyValue, migration));

		assertFalse(value.getValue());
	}
}
