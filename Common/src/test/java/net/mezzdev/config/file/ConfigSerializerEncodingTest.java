package net.mezzdev.config.file;

import net.mezzdev.config.api.value.color.ConfigColorFormat;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.color.PackedColor;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.ColorSerializer;
import net.mezzdev.config.serializers.DoubleSerializer;
import net.mezzdev.config.serializers.EnumSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.LongSerializer;
import net.mezzdev.config.serializers.StringSerializer;
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

public class ConfigSerializerEncodingTest {
	private static final String LOCALIZATION_PATH = "mezz_config.config.test.values";

	@Test
	public void canonicalConfigFileEncodingRoundTripsBuiltInScalarsAndLists(@TempDir Path tempDir) throws IOException {
		// Setup: one category contains every built-in scalar plus string and boolean lists with sensitive text.
		Path path = tempDir.resolve("values.ini");
		PackedColor color = new PackedColor(0x12ABEF, ConfigColorFormat.RGB);
		ConfigValue<String> text = value("text", "default", StringSerializer.INSTANCE);
		ConfigValue<Boolean> enabled = value("enabled", true, BooleanSerializer.INSTANCE);
		ConfigValue<Integer> integer = value("integer", 1, new IntegerSerializer(Integer.MIN_VALUE, Integer.MAX_VALUE));
		ConfigValue<Long> longValue = value("longValue", 2L, new LongSerializer(Long.MIN_VALUE, Long.MAX_VALUE));
		ConfigValue<Double> decimal = value("decimal", 3.0, new DoubleSerializer(-Double.MAX_VALUE, Double.MAX_VALUE));
		ConfigValue<Mode> mode = value("mode", Mode.FIRST, new EnumSerializer<>(Mode.class));
		ConfigValue<PackedColor> packedColor = value("packedColor", PackedColor.rgb(0), ColorSerializer.INSTANCE);
		ConfigValue<List<String>> strings = value("strings", List.of(), new ListSerializer<>(StringSerializer.INSTANCE));
		ConfigValue<List<Boolean>> booleans = value("booleans", List.of(), new ListSerializer<>(BooleanSerializer.INSTANCE));
		ConfigCategory category = category(text, enabled, integer, longValue, decimal, mode, packedColor, strings, booleans);

		text.set("text");
		enabled.set(false);
		integer.set(-7);
		longValue.set(Long.MAX_VALUE);
		decimal.set(2.5);
		mode.set(Mode.SECOND);
		packedColor.set(color);
		strings.set(List.of("", "a,b", " surrounding ", "line one\nline two"));
		booleans.set(List.of(true, false));

		// Operation: save the values, reset them, and load the saved file.
		ConfigSerializer.save(path, List.of(category));
		category.getConfigValues().forEach(ConfigValue::resetToDefaultWithoutNotifying);
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: all value types round-trip and use the canonical human-readable encoding.
		assertEquals("text", text.get());
		assertFalse(enabled.get());
		assertEquals(-7, integer.get());
		assertEquals(Long.MAX_VALUE, longValue.get());
		assertEquals(2.5, decimal.get());
		assertEquals(Mode.SECOND, mode.get());
		assertEquals(color, packedColor.get());
		assertEquals(List.of("", "a,b", " surrounding ", "line one\nline two"), strings.get());
		assertEquals(List.of(true, false), booleans.get());

		String saved = Files.readString(path);
		assertTrue(saved.contains("enabled = false"));
		assertTrue(saved.contains("integer = -7"));
		assertTrue(saved.contains("# Valid Values: A bracketed list containing values of:"));
		assertTrue(saved.contains("strings = [\"\",\"a,b\",\" surrounding \",\"line one\\nline two\"]"));
	}

	@Test
	public void emptyListRoundTripsWithCanonicalEncoding(@TempDir Path tempDir) throws IOException {
		// Setup: a string list has a non-empty default and an empty selected value.
		Path path = tempDir.resolve("empty-list.ini");
		ConfigValue<List<String>> strings = value("strings", List.of("default"), new ListSerializer<>(StringSerializer.INSTANCE));
		ConfigCategory category = category(strings);
		strings.set(List.of());

		// Operation: save the empty list, replace it in memory, and load it again.
		ConfigSerializer.save(path, List.of(category));
		strings.set(List.of("changed"));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: an empty list remains distinct and uses its canonical encoding.
		assertEquals(List.of(), strings.get());
		assertTrue(Files.readString(path).contains("strings = []"));
	}

	@Test
	public void stringsAndCustomSerializerOutputRoundTripLosslessly(@TempDir Path tempDir) throws IOException {
		// Setup: built-in, custom scalar, and custom list serializers receive delimiter-sensitive Unicode text.
		Path path = tempDir.resolve("strings.ini");
		IdentitySerializer serializer = new IdentitySerializer();
		ConfigValue<String> builtIn = value("builtIn", "default", StringSerializer.INSTANCE);
		ConfigValue<String> custom = value("custom", "default", serializer);
		ConfigValue<List<String>> customList = value("customList", List.of(), new ListSerializer<>(serializer));
		ConfigCategory category = category(builtIn, custom, customList);
		String sensitive = " [section] = value, # text\n\"quoted\" \\ path こんにちは ";
		List<String> elements = List.of("", "a,b", " surrounding ", sensitive);
		builtIn.set(sensitive);
		custom.set(sensitive);
		customList.set(elements);

		// Operation: save the values, replace them in memory, and reload the file.
		ConfigSerializer.save(path, List.of(category));
		builtIn.set("changed");
		custom.set("changed");
		customList.set(List.of("changed"));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: every value round-trips while embedded newlines and quotes stay escaped on disk.
		assertEquals(sensitive, builtIn.get());
		assertEquals(sensitive, custom.get());
		assertEquals(elements, customList.get());
		assertFalse(Files.readString(path).contains("\n\"quoted\""));
		assertTrue(Files.readString(path).contains("\\n\\\"quoted\\\""));
	}

	@Test
	public void malformedEncodedValueDoesNotBlockValidNeighbors(@TempDir Path tempDir) throws IOException {
		// Setup: a malformed quoted value is stored between two valid entries.
		Path path = tempDir.resolve("damaged.ini");
		Files.write(path, List.of(
			"[values]",
			"before = 8",
			"broken = \"unterminated",
			"after = false"
		));
		ConfigValue<Integer> before = value("before", 1, new IntegerSerializer(0, 10));
		ConfigValue<String> broken = value("broken", "fallback", StringSerializer.INSTANCE);
		ConfigValue<Boolean> after = value("after", true, BooleanSerializer.INSTANCE);
		ConfigCategory category = category(before, broken, after);

		// Operation: load the damaged category.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: valid neighbors load, the malformed value falls back, and the source is backed up.
		assertEquals(8, before.get());
		assertEquals("fallback", broken.get());
		assertFalse(after.get());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void malformedCategoryCannotAttachValuesToThePreviousCategory(@TempDir Path tempDir) throws IOException {
		// Setup: a valid category is followed by a malformed header and a conflicting value.
		Path path = tempDir.resolve("category.ini");
		Files.write(path, List.of(
			"[values]",
			"enabled = false",
			"[broken",
			"enabled = true"
		));
		ConfigValue<Boolean> enabled = value("enabled", true, BooleanSerializer.INSTANCE);
		ConfigCategory category = category(enabled);

		// Operation: load the file with the malformed category boundary.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: the value after the malformed header does not overwrite the preceding category value.
		assertFalse(enabled.get());
	}

	@Test
	public void duplicateValueUsesTheLastUsableDeclarationAndIsCorrected(@TempDir Path tempDir) throws IOException {
		// Setup: one value is declared repeatedly with valid and invalid encodings before a valid neighbor.
		Path path = tempDir.resolve("duplicate.ini");
		Files.write(path, List.of(
			"[values]",
			"enabled = false",
			"enabled = invalid",
			"enabled = true",
			"after = 8"
		));
		ConfigValue<Boolean> enabled = value("enabled", false, BooleanSerializer.INSTANCE);
		ConfigValue<Integer> after = value("after", 1, new IntegerSerializer(0, 10));
		ConfigCategory category = category(enabled, after);

		// Operation: load and correct the duplicate declarations.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: the last usable value wins, its neighbor loads, and the rewrite keeps one declaration.
		assertTrue(enabled.get());
		assertEquals(8, after.get());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertEquals(1, Files.readAllLines(path).stream().filter(line -> line.stripLeading().startsWith("enabled =")).count());
	}

	private static <T> ConfigValue<T> value(String name, T defaultValue, IConfigValueSerializer<T> serializer) {
		return new ConfigValue<>(LOCALIZATION_PATH, name, defaultValue, serializer);
	}

	private static ConfigCategory category(ConfigValue<?>... values) {
		return new ConfigCategory(LOCALIZATION_PATH, "values", List.of(values));
	}

	private enum Mode {
		FIRST,
		SECOND
	}

	private static final class IdentitySerializer implements IConfigValueSerializer<String> {
		@Override
		public String serialize(String value) {
			return value;
		}

		@Override
		public IDeserializeResult<String> deserialize(String string) {
			return IDeserializeResult.success(string);
		}

		@Override
		public boolean isValid(String value) {
			return value != null;
		}

		@Override
		public String getValidValuesDescription() {
			return "Any string";
		}
	}
}
