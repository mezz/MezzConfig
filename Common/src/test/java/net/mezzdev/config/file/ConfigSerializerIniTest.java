package net.mezzdev.config.file;

import net.mezzdev.config.api.value.ConfigColorFormat;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.PackedColor;
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

public class ConfigSerializerIniTest {
	private static final String LOCALIZATION_PATH = "mezz_config.config.test.values";

	@Test
	public void canonicalIniRoundTripsBuiltInScalarsAndLists(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.save(path, List.of(category));
		category.getConfigValues().forEach(ConfigValue::resetToDefaultWithoutNotifying);
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals("text", text.getValue());
		assertFalse(enabled.getValue());
		assertEquals(-7, integer.getValue());
		assertEquals(Long.MAX_VALUE, longValue.getValue());
		assertEquals(2.5, decimal.getValue());
		assertEquals(Mode.SECOND, mode.getValue());
		assertEquals(color, packedColor.getValue());
		assertEquals(List.of("", "a,b", " surrounding ", "line one\nline two"), strings.getValue());
		assertEquals(List.of(true, false), booleans.getValue());

		String saved = Files.readString(path);
		assertTrue(saved.contains("enabled = false"));
		assertTrue(saved.contains("integer = -7"));
		assertTrue(saved.contains("# Valid Values: A bracketed list containing values of:"));
		assertTrue(saved.contains("strings = [\"\",\"a,b\",\" surrounding \",\"line one\\nline two\"]"));

		strings.set(List.of());
		ConfigSerializer.save(path, List.of(category));
		strings.set(List.of("changed"));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals(List.of(), strings.getValue());
		assertTrue(Files.readString(path).contains("strings = []"));
	}

	@Test
	public void stringsAndCustomSerializerOutputRoundTripLosslessly(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.save(path, List.of(category));
		builtIn.set("changed");
		custom.set("changed");
		customList.set(List.of("changed"));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals(sensitive, builtIn.getValue());
		assertEquals(sensitive, custom.getValue());
		assertEquals(elements, customList.getValue());
		assertFalse(Files.readString(path).contains("\n\"quoted\""));
		assertTrue(Files.readString(path).contains("\\n\\\"quoted\\\""));
	}

	@Test
	public void malformedEncodedValueDoesNotBlockValidNeighbors(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals(8, before.getValue());
		assertEquals("fallback", broken.getValue());
		assertFalse(after.getValue());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void malformedCategoryCannotAttachValuesToThePreviousCategory(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("category.ini");
		Files.write(path, List.of(
			"[values]",
			"enabled = false",
			"[broken",
			"enabled = true"
		));
		ConfigValue<Boolean> enabled = value("enabled", true, BooleanSerializer.INSTANCE);
		ConfigCategory category = category(enabled);

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertFalse(enabled.getValue());
	}

	@Test
	public void duplicateValueUsesTheLastUsableDeclarationAndIsCorrected(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertTrue(enabled.getValue());
		assertEquals(8, after.getValue());
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
