package net.mezzdev.config.test.file;

import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSerializerLoadSaveTest {
	private static final String LOCALIZATION_PATH = "mezz_config.config.test.current";

	@Test
	public void loadUpdatesValuesFromKnownCategory(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"enabled = false",
			"count = 7"
		));
		ConfigValue<Boolean> enabled = createBooleanValue(true);
		ConfigValue<Integer> count = createIntegerValue(1);
		ConfigCategory category = createCategory(enabled, count);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
		assertEquals(7, count.getValue());
	}

	@Test
	public void loadLeavesValueUnchangedWhenDeserializationFails(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"enabled = maybe",
			"count = 11"
		));
		ConfigValue<Boolean> enabled = createBooleanValue(true);
		ConfigValue<Integer> count = createIntegerValue(1);
		ConfigCategory category = createCategory(enabled, count);

		ConfigSerializer.load(path, List.of(category));

		assertTrue(enabled.getValue());
		assertEquals(1, count.getValue());
	}

	@Test
	public void saveCreatesParentDirectoriesAndSerializesValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("nested").resolve("test.ini");
		ConfigValue<Boolean> enabled = createBooleanValue(true);
		ConfigValue<Integer> count = createIntegerValue(1);
		ConfigCategory category = createCategory(enabled, count);
		enabled.set(false);
		count.set(7);

		ConfigSerializer.save(path, List.of(category));

		List<String> lines = Files.readAllLines(path);
		assertTrue(Files.exists(path));
		assertTrue(lines.contains("[current]"));
		assertTrue(lines.contains("\t# Name: mezz_config.config.test.current.enabled"));
		assertTrue(lines.contains("\t# Description: mezz_config.config.test.current.enabled.description"));
		assertTrue(lines.contains("\t# Valid Values: [true, false]"));
		assertTrue(lines.contains("\t# Default Value: true"));
		assertTrue(lines.contains("\tenabled = false"));
		assertTrue(lines.contains("\tcount = 7"));
	}

	private static ConfigValue<Boolean> createBooleanValue(boolean defaultValue) {
		return new ConfigValue<>(
			LOCALIZATION_PATH,
			"enabled",
			defaultValue,
			BooleanSerializer.INSTANCE
		);
	}

	private static ConfigValue<Integer> createIntegerValue(int defaultValue) {
		return new ConfigValue<>(
			LOCALIZATION_PATH,
			"count",
			defaultValue,
			new IntegerSerializer(0, 10)
		);
	}

	private static ConfigCategory createCategory(ConfigValue<?>... values) {
		return new ConfigCategory(
			LOCALIZATION_PATH,
			"current",
			List.of(values)
		);
	}
}
