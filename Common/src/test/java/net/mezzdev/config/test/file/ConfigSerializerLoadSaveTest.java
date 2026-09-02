package net.mezzdev.config.test.file;

import net.mezzdev.config.api.value.ConfigValueEditMode;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.file.ConfigSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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

		assertFalse(enabled.get());
		assertEquals(7, count.get());
	}

	@Test
	public void loadStagesValuesWithRestartRequirements(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"enabled = false"
		));
		ConfigValue<Boolean> enabled = createBooleanValue(true, ConfigValueRestartRequirement.GAME_RESTART);
		ConfigCategory category = createCategory(enabled);

		ConfigSerializer.load(path, List.of(category));

		assertTrue(enabled.get());
		assertFalse(enabled.getEditorInfo().getPendingValue());
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

		assertTrue(enabled.get());
		assertEquals(1, count.get());
	}

	@Test
	public void loadLeavesListUnchangedWhenNoElementsCanBeDeserialized(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"values = invalid, also-invalid"
		));
		ConfigValue<List<Integer>> values = new ConfigValue<>(
			LOCALIZATION_PATH,
			"values",
			List.of(1, 2),
			new ListSerializer<>(new IntegerSerializer(0, 10))
		);
		values.set(List.of(3, 4));
		ConfigCategory category = createCategory(values);

		ConfigSerializer.load(path, List.of(category));

		assertEquals(List.of(3, 4), values.get());
		assertEquals(List.of(1, 2), values.getEditorInfo().getDefaultValue());
	}

	@Test
	public void loadRejectsStructuredListsThatViolateWholeListValidation(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[current]",
			"values = [\"duplicate\",\"duplicate\"]"
		));
		ConfigValue<List<String>> values = new ConfigValue<>(
			LOCALIZATION_PATH,
			"values",
			List.of("default"),
			new UniqueNonEmptyStringListSerializer()
		);
		values.set(List.of("current"));
		ConfigCategory category = createCategory(values);

		ConfigSerializer.load(path, List.of(category));

		assertEquals(List.of("current"), values.get());
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
		count.addBatchListener(changes -> batchChanges.add(formatBatch(changes, true, count.get())));

		List<? extends IAppliedConfigValueChange<?>> changes = ConfigSerializer.load(path, List.of(category));

		assertEquals(List.of(), changes);
		assertEquals(1, count.get());
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
		enabled.addListener(change -> regularChanges.add("%s -> %s, count = %s".formatted(change.oldValue(), change.newValue(), count.get())));
		enabled.addBatchListener(changes -> enabledBatches.add(formatBatch(changes, enabled.get(), count.get())));
		count.addBatchListener(changes -> countBatches.add(formatBatch(changes, enabled.get(), count.get())));

		ConfigSerializer.load(path, List.of(category));

		assertFalse(enabled.get());
		assertEquals(7, count.get());
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
	public void saveDefaultsUsesDeclaredValuesInsteadOfPlayerValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		ConfigValue<Boolean> enabled = createBooleanValue(true);
		ConfigCategory category = createCategory(enabled);
		enabled.set(false);

		ConfigSerializer.saveDefaults(path, List.of(category));

		List<String> lines = Files.readAllLines(path);
		assertTrue(lines.contains("\tenabled = true"));
		assertFalse(lines.contains("\tenabled = false"));
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

	private static final class UniqueNonEmptyStringListSerializer implements IConfigListValueSerializer<String> {
		@Override
		public IConfigValueSerializer<String> getElementSerializer() {
			return StringSerializer.INSTANCE;
		}

		@Override
		public String serialize(List<String> value) {
			return String.join(",", value);
		}

		@Override
		public IDeserializeResult<List<String>> deserialize(String string) {
			return IDeserializeResult.success(List.of(string.split(",")));
		}

		@Override
		public boolean isValid(List<String> value) {
			return !value.isEmpty() && value.size() == new HashSet<>(value).size();
		}

		@Override
		public String getValidValuesDescription() {
			return "A non-empty list without duplicate values.";
		}
	}

	private static String formatBatch(
		List<? extends IAppliedConfigValueChange<?>> changes,
		boolean enabled,
		int count
	) {
		String formattedChanges = String.join(", ", changes.stream()
			.map(change -> "%s: %s -> %s".formatted(
				change.configValue().getEditorInfo().getName(),
				change.oldValue(),
				change.newValue()
			))
			.toList());
		return "%s; enabled = %s; count = %s".formatted(
			formattedChanges,
			enabled,
			count
		);
	}
}
