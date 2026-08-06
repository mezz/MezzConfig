package net.mezzdev.config.test.file;

import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
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
import java.util.ArrayList;
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
	public void loadUpdatesValuesWithRestartRequirements(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"enabled = false"
		));
		ConfigValue<Boolean> enabled = createBooleanValue(true, ConfigValueRestartRequirement.GAME_RESTART);
		ConfigCategory category = createCategory(enabled);

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
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
	public void loadDoesNotNotifyWhenValueIsEqual(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"count = 1"
		));
		ConfigValue<Integer> count = createIntegerValue(1);
		ConfigCategory category = createCategory(count);
		List<String> regularChanges = new ArrayList<>();
		List<String> batchChanges = new ArrayList<>();
		count.addListener(change -> regularChanges.add("%s -> %s".formatted(change.oldValue(), change.newValue())));
		count.addBatchListener(changes -> batchChanges.add(formatBatch(changes, true, count.getValue())));

		List<? extends IAppliedConfigValueChange<?>> changes = ConfigSerializer.load(path, List.of(category));

		assertEquals(List.of(), changes);
		assertEquals(1, count.getValue());
		assertEquals(List.of(), regularChanges);
		assertEquals(List.of(), batchChanges);
	}

	@Test
	public void loadNotifiesListenersAfterAllValuesUpdate(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"enabled = false",
			"count = 7"
		));
		ConfigValue<Boolean> enabled = createBooleanValue(true);
		ConfigValue<Integer> count = createIntegerValue(1);
		ConfigCategory category = createCategory(enabled, count);
		List<String> regularChanges = new ArrayList<>();
		List<String> enabledBatches = new ArrayList<>();
		List<String> countBatches = new ArrayList<>();
		enabled.addListener(change -> regularChanges.add("%s -> %s, count = %s".formatted(change.oldValue(), change.newValue(), count.getValue())));
		enabled.addBatchListener(changes -> enabledBatches.add(formatBatch(changes, enabled.getValue(), count.getValue())));
		count.addBatchListener(changes -> countBatches.add(formatBatch(changes, enabled.getValue(), count.getValue())));

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.getValue());
		assertEquals(7, count.getValue());
		assertEquals(List.of("true -> false, count = 7"), regularChanges);
		assertEquals(List.of("enabled: true -> false, count: 1 -> 7; enabled = false; count = 7"), enabledBatches);
		assertEquals(List.of("enabled: true -> false, count: 1 -> 7; enabled = false; count = 7"), countBatches);
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
		assertEquals(List.of(
			"# Name: mezz_config.config.test.current",
			"# Description: mezz_config.config.test.current.description",
			"[current]"
		), lines.subList(0, 3));
		assertTrue(lines.contains("[current]"));
		assertTrue(lines.contains("\t# Name: mezz_config.config.test.current.enabled"));
		assertTrue(lines.contains("\t# Description: mezz_config.config.test.current.enabled.description"));
		assertTrue(lines.contains("\t# Valid Values: [true, false]"));
		assertTrue(lines.contains("\t# Default Value: true"));
		assertFalse(lines.contains("\t# Requires a world restart to take effect."));
		assertFalse(lines.contains("\t# Requires a game restart to take effect."));
		assertTrue(lines.contains("\tenabled = false"));
		assertTrue(lines.contains("\tcount = 7"));
	}

	@Test
	public void saveNotesWhenValueRequiresGameRestart(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		ConfigValue<Boolean> enabled = createBooleanValue(true, ConfigValueRestartRequirement.GAME_RESTART);
		ConfigCategory category = createCategory(enabled);

		ConfigSerializer.save(path, List.of(category));

		List<String> lines = Files.readAllLines(path);
		assertTrue(lines.contains("\t# Requires a game restart to take effect."));
	}

	@Test
	public void saveNotesWhenValueRequiresWorldRestart(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		ConfigValue<Boolean> enabled = createBooleanValue(true, ConfigValueRestartRequirement.WORLD_RESTART);
		ConfigCategory category = createCategory(enabled);

		ConfigSerializer.save(path, List.of(category));

		List<String> lines = Files.readAllLines(path);
		assertTrue(lines.contains("\t# Requires a world restart to take effect."));
	}

	private static ConfigValue<Boolean> createBooleanValue(boolean defaultValue) {
		return createBooleanValue(defaultValue, ConfigValueEditMode.BATCH);
	}

	private static ConfigValue<Boolean> createBooleanValue(boolean defaultValue, ConfigValueEditMode editMode) {
		return createBooleanValue(defaultValue, editMode, ConfigValueRestartRequirement.NONE);
	}

	private static ConfigValue<Boolean> createBooleanValue(boolean defaultValue, ConfigValueRestartRequirement restartRequirement) {
		return createBooleanValue(defaultValue, ConfigValueEditMode.BATCH, restartRequirement);
	}

	private static ConfigValue<Boolean> createBooleanValue(
		boolean defaultValue,
		ConfigValueEditMode editMode,
		ConfigValueRestartRequirement restartRequirement
	) {
		return new ConfigValue<>(
			LOCALIZATION_PATH,
			"enabled",
			defaultValue,
			BooleanSerializer.INSTANCE,
			editMode,
			restartRequirement,
			List.of()
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
}
