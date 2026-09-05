package net.mezzdev.config.test.serializers;

import net.mezzdev.config.api.value.color.ConfigColorFormat;
import net.mezzdev.config.api.value.serializer.ConfigListOrdering;
import net.mezzdev.config.api.value.serializer.ConfigValueRange;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigKeyValueSerializer;
import net.mezzdev.config.api.value.serializer.IConfigListValueSerializer;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.color.PackedColor;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.ColorSerializer;
import net.mezzdev.config.serializers.DoubleSerializer;
import net.mezzdev.config.serializers.EnumSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.serializers.LongSerializer;
import net.mezzdev.config.serializers.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigValueSerializerTest {
	@Test
	public void booleanSerializerParsesTrimmedCaseInsensitiveValues() {
		assertEquals(true, deserializeValue(BooleanSerializer.INSTANCE, " TRUE "));
		assertEquals(false, deserializeValue(BooleanSerializer.INSTANCE, " false "));
		assertEquals(List.of(true, false), List.copyOf(BooleanSerializer.INSTANCE.getAllValidValues().orElseThrow()));
	}

	@Test
	public void booleanSerializerRejectsInvalidValues() {
		IDeserializeResult<Boolean> result = BooleanSerializer.INSTANCE.deserialize("enabled");

		assertTrue(result.getResult().isEmpty());
		assertEquals(List.of("string must be 'true' or 'false'"), result.getDiagnostics());
	}

	@Test
	public void stringSerializerPreservesValues() {
		assertEquals("hello world", deserializeValue(StringSerializer.INSTANCE, "hello world"));
		assertEquals("", deserializeValue(StringSerializer.INSTANCE, ""));
		assertEquals("hello world", StringSerializer.INSTANCE.serialize("hello world"));
		assertTrue(StringSerializer.INSTANCE.isValid(""));
		assertFalse(StringSerializer.INSTANCE.isValid(null));
	}

	@Test
	public void integerSerializerParsesAndValidatesBounds() {
		IntegerSerializer serializer = new IntegerSerializer(2, 4);

		assertEquals(2, deserializeValue(serializer, "2"));
		assertEquals(4, deserializeValue(serializer, "4"));
		assertTrue(serializer.isValid(3));
		assertFalse(serializer.isValid(1));
		assertFalse(serializer.isValid(5));
		assertEquals(new ConfigValueRange<>(2, 4), serializer.getRange().orElseThrow());
		assertEquals(List.of(2, 3, 4), List.copyOf(serializer.getAllValidValues().orElseThrow()));
	}

	@Test
	public void integerSerializerRejectsInvalidValues() {
		IntegerSerializer serializer = new IntegerSerializer(2, 4);

		IDeserializeResult<Integer> outOfRange = serializer.deserialize("5");
		IDeserializeResult<Integer> notAnInteger = serializer.deserialize("five");

		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid integer. Must be: An integer in the range [2, 4] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notAnInteger.getResult().isEmpty());
		assertTrue(notAnInteger.getDiagnostics().getFirst().contains("Unable to parse int: 'five'"));
	}

	@Test
	public void integerSerializerOmitsAllValidValuesForLargeRanges() {
		IntegerSerializer serializer = new IntegerSerializer(0, 20);

		assertTrue(serializer.getAllValidValues().isEmpty());
	}

	@Test
	public void colorSerializerParsesAndSerializesRgbAndArgbHexColors() {
		PackedColor rgb = PackedColor.rgb(0x112233);
		PackedColor argb = PackedColor.argb(0x80112233);

		assertEquals("0x112233", ColorSerializer.INSTANCE.serialize(rgb));
		assertEquals("0x80112233", ColorSerializer.INSTANCE.serialize(argb));
		assertEquals(rgb, deserializeValue(ColorSerializer.INSTANCE, "0x112233"));
		assertEquals(argb, deserializeValue(ColorSerializer.INSTANCE, "0x80112233"));
		assertEquals(PackedColor.argb(0x80ABCDEF), deserializeValue(ColorSerializer.INSTANCE, "0x80abcdef"));
		assertTrue(ColorSerializer.INSTANCE.isValid(rgb));
		assertFalse(ColorSerializer.INSTANCE.isValid(null));
	}

	@Test
	public void packedColorRejectsArgbDataInRgbFormat() {
		assertThrows(IllegalArgumentException.class, () -> PackedColor.rgb(0xFF112233));
	}

	@Test
	public void colorSerializerRejectsInvalidColors() {
		IDeserializeResult<PackedColor> missingPrefix = ColorSerializer.INSTANCE.deserialize("FF112233");
		IDeserializeResult<PackedColor> shortColor = ColorSerializer.INSTANCE.deserialize("0x12345");
		IDeserializeResult<PackedColor> invalidHex = ColorSerializer.INSTANCE.deserialize("0xGG112233");
		IDeserializeResult<PackedColor> quotedColor = ColorSerializer.INSTANCE.deserialize("\"0x112233\"");

		assertTrue(missingPrefix.getResult().isEmpty());
		assertEquals(
			List.of("Invalid color. Must be: An RGB or ARGB color serialized as 0xRRGGBB or 0xAARRGGBB"),
			missingPrefix.getDiagnostics()
		);
		assertTrue(shortColor.getResult().isEmpty());
		assertEquals(
			List.of("Invalid color. Must be: An RGB or ARGB color serialized as 0xRRGGBB or 0xAARRGGBB"),
			shortColor.getDiagnostics()
		);
		assertTrue(invalidHex.getResult().isEmpty());
		assertTrue(invalidHex.getDiagnostics().getFirst().contains("Unable to parse color: '0xGG112233'"));
		assertTrue(quotedColor.getResult().isEmpty());
	}

	@Test
	public void longSerializerParsesAndValidatesBounds() {
		LongSerializer serializer = new LongSerializer(2L, 4L);

		assertEquals(2L, deserializeValue(serializer, "2"));
		assertEquals(4L, deserializeValue(serializer, "4"));
		assertTrue(serializer.isValid(3L));
		assertFalse(serializer.isValid(1L));
		assertFalse(serializer.isValid(5L));
		assertEquals(new ConfigValueRange<>(2L, 4L), serializer.getRange().orElseThrow());
		assertEquals(List.of(2L, 3L, 4L), List.copyOf(serializer.getAllValidValues().orElseThrow()));
	}

	@Test
	public void longSerializerRejectsInvalidValues() {
		LongSerializer serializer = new LongSerializer(2L, 4L);

		IDeserializeResult<Long> outOfRange = serializer.deserialize("5");
		IDeserializeResult<Long> notALong = serializer.deserialize("five");

		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid long. Must be: A long in the range [2, 4] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notALong.getResult().isEmpty());
		assertTrue(notALong.getDiagnostics().getFirst().contains("Unable to parse long: 'five'"));
	}

	@Test
	public void longSerializerOmitsAllValidValuesForLargeRanges() {
		LongSerializer serializer = new LongSerializer(0, 20);

		assertTrue(serializer.getAllValidValues().isEmpty());
	}

	@Test
	public void doubleSerializerParsesAndValidatesBounds() {
		DoubleSerializer serializer = new DoubleSerializer(0.5, 2.5);

		assertEquals(0.5, deserializeValue(serializer, "0.5"));
		assertEquals(2.5, deserializeValue(serializer, "2.5"));
		assertTrue(serializer.isValid(1.5));
		assertFalse(serializer.isValid(0.25));
		assertFalse(serializer.isValid(3.0));
		assertFalse(serializer.isValid(Double.NaN));
		assertFalse(serializer.isValid(Double.POSITIVE_INFINITY));
		assertEquals(new ConfigValueRange<>(0.5, 2.5), serializer.getRange().orElseThrow());
		assertTrue(serializer.getAllValidValues().isEmpty());
	}

	@Test
	public void doubleSerializerRejectsInvalidValues() {
		DoubleSerializer serializer = new DoubleSerializer(0.5, 2.5);

		IDeserializeResult<Double> outOfRange = serializer.deserialize("3.0");
		IDeserializeResult<Double> notADouble = serializer.deserialize("many");

		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid double. Must be: A finite double in the range [0.5, 2.5] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notADouble.getResult().isEmpty());
		assertTrue(notADouble.getDiagnostics().getFirst().contains("Unable to parse double: 'many'"));
	}

	@Test
	public void enumSerializerSerializesNamesAndReportsValidValues() {
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class);

		assertEquals("FIRST_VALUE", serializer.serialize(TestEnum.FIRST_VALUE));
		assertEquals(TestEnum.FIRST_VALUE, deserializeValue(serializer, "FIRST_VALUE"));
		assertEquals("[FIRST_VALUE, SECOND_VALUE]", serializer.getValidValuesDescription());
		assertEquals(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE), List.copyOf(serializer.getAllValidValues().orElseThrow()));
	}

	@Test
	public void enumSerializerRejectsInvalidNames() {
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class);

		IDeserializeResult<TestEnum> result = serializer.deserialize("MISSING");
		IDeserializeResult<TestEnum> quotedResult = serializer.deserialize("\"FIRST_VALUE\"");

		assertTrue(result.getResult().isEmpty());
		assertTrue(result.getDiagnostics().getFirst().contains("Invalid enum name"));
		assertTrue(quotedResult.getResult().isEmpty());
	}

	@Test
	public void enumSerializerSupportsRestrictedValidValues() {
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class, List.of(TestEnum.SECOND_VALUE));

		assertEquals(TestEnum.SECOND_VALUE, deserializeValue(serializer, "SECOND_VALUE"));
		assertEquals("[SECOND_VALUE]", serializer.getValidValuesDescription());
		assertEquals(List.of(TestEnum.SECOND_VALUE), List.copyOf(serializer.getAllValidValues().orElseThrow()));
		assertFalse(serializer.isValid(TestEnum.FIRST_VALUE));
		assertTrue(serializer.deserialize("FIRST_VALUE").getDiagnostics().getFirst().contains("Invalid enum name"));
	}

	@Test
	public void enumSerializerRejectsInvalidValidValueLists() {
		assertThrows(IllegalArgumentException.class, () -> new EnumSerializer<>(TestEnum.class, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new EnumSerializer<>(TestEnum.class, List.of(TestEnum.FIRST_VALUE, TestEnum.FIRST_VALUE)));
	}

	@Test
	public void listSerializerDeserializesStructuredValues() {
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		assertEquals(List.of(true, false, true), deserializeValue(serializer, "[\"true\",\"false\",\"TRUE\"]"));
		assertEquals(List.of(), deserializeValue(serializer, "[]"));
		assertEquals("[\"true\",\"false\"]", serializer.serialize(List.of(true, false)));
		assertTrue(serializer.isValid(List.of(true, false)));
	}

	@Test
	public void listSerializerRejectsMissingClosingBracket() {
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[true, false");

		assertTrue(result.getResult().isEmpty());
		assertEquals(1, result.getDiagnostics().size());
		assertTrue(result.getDiagnostics().getFirst().startsWith("Invalid JSON value"));
	}

	@Test
	public void listSerializerReportsPartialSuccessForRecoveredElements() {
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[\"true\",\"invalid\",\"false\"]");

		assertEquals(List.of(true, false), result.getResult().orElseThrow());
		assertEquals(List.of("Array element 1: string must be 'true' or 'false'"), result.getDiagnostics());
	}

	@Test
	public void listSerializerReportsFailureWhenNoElementsAreRecovered() {
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[\"invalid\",\"also-invalid\"]");

		assertTrue(result.getResult().isEmpty());
		assertEquals(
			List.of(
				"Array element 0: string must be 'true' or 'false'",
				"Array element 1: string must be 'true' or 'false'"
			),
			result.getDiagnostics()
		);
	}

	@Test
	public void listSerializerRejectsUnstructuredValues() {
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		IDeserializeResult<List<Boolean>> result = serializer.deserialize("true, false");

		assertTrue(result.getResult().isEmpty());
		assertEquals(List.of("Expected a structured array."), result.getDiagnostics());
	}

	@Test
	public void listSerializerWrapsElementSerializer() {
		IConfigValueSerializer<List<Boolean>> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);
		IConfigListValueSerializer<Boolean> unorderedSerializer = new ListSerializer<>(
			BooleanSerializer.INSTANCE,
			ConfigListOrdering.UNORDERED
		);

		assertEquals(List.of(true, false), deserializeValue(serializer, "[\"true\",\"false\"]"));
		assertEquals("[\"true\",\"false\"]", serializer.serialize(List.of(true, false)));
		assertTrue(serializer instanceof IConfigListValueSerializer<?>);
		IConfigListValueSerializer<?> listSerializer = (IConfigListValueSerializer<?>) serializer;
		assertSame(BooleanSerializer.INSTANCE, listSerializer.getElementSerializer());
		assertEquals(ConfigListOrdering.ORDERED, listSerializer.getOrdering());
		assertEquals(ConfigListOrdering.UNORDERED, unorderedSerializer.getOrdering());
	}

	@Test
	public void listSerializerSupportsEnumElementSerializers() {
		ListSerializer<TestEnum> serializer = new ListSerializer<>(new EnumSerializer<>(TestEnum.class));

		assertEquals(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE), deserializeValue(serializer, "[\"FIRST_VALUE\",\"SECOND_VALUE\"]"));
		assertEquals("[\"FIRST_VALUE\",\"SECOND_VALUE\"]", serializer.serialize(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE)));
		assertEquals("A list containing values of:\n[FIRST_VALUE, SECOND_VALUE]", serializer.getValidValuesDescription());
	}

	@Test
	public void listSerializerReadsLosslessStructuredStrings() {
		ListSerializer<String> serializer = new ListSerializer<>(StringSerializer.INSTANCE);
		List<String> expected = List.of("", "a,b", " surrounding ", "[section]", "line one\nline two", "\\path");
		String encoded = "[\"\", \"a,b\", \" surrounding \", \"[section]\", \"line one\\nline two\", \"\\\\path\"]";

		assertEquals(expected, deserializeValue(serializer, encoded));
		assertEquals(expected, deserializeValue(serializer, serializer.serialize(expected)));
	}

	@Test
	public void keyValueSerializerExposesComponentsWithoutChangingStorageFormat() {
		// Setup: model JEI's ordered named colors as domain values instead of changing them to a map.
		NamedColorSerializer serializer = new NamedColorSerializer();
		ListSerializer<NamedColor> listSerializer = new ListSerializer<>(serializer);
		NamedColor lightBlue = new NamedColor("LightBlue", PackedColor.rgb(0x7492CC));

		// Operation: serialize the containing value with its existing custom format.
		String serialized = serializer.serialize(lightBlue);
		NamedColor deserialized = deserializeValue(serializer, serialized);

		// Assertions: integrations can edit each component with its own serializer and rebuild the domain value.
		assertEquals("LightBlue:7492CC", serialized);
		assertEquals(lightBlue, deserialized);
		assertSame(StringSerializer.INSTANCE, serializer.getKeySerializer());
		assertSame(ColorSerializer.INSTANCE, serializer.getValueSerializer());
		assertSame(serializer, listSerializer.getElementSerializer());
		assertEquals("LightBlue", serializer.getKey(lightBlue));
		assertEquals(PackedColor.rgb(0x7492CC), serializer.getValue(lightBlue));
		assertEquals(
			new NamedColor("Blue", PackedColor.rgb(0x2222DD)),
			serializer.createEntry("Blue", PackedColor.rgb(0x2222DD))
		);
	}

	@Test
	public void deserializeResultFactoriesCreateStandardResults() {
		IDeserializeResult<String> success = IDeserializeResult.success("value");
		IDeserializeResult<String> failure = IDeserializeResult.failure("error");
		IDeserializeResult<String> partial = IDeserializeResult.partialSuccess("partial", List.of("warning"));

		assertEquals("value", success.getResult().orElseThrow());
		assertEquals(List.of(), success.getDiagnostics());
		assertTrue(failure.getResult().isEmpty());
		assertEquals(List.of("error"), failure.getDiagnostics());
		assertEquals("partial", partial.getResult().orElseThrow());
		assertEquals(List.of("warning"), partial.getDiagnostics());
	}

	@Test
	public void deserializeResultFactoriesRejectInvalidStates() {
		assertThrows(NullPointerException.class, () -> IDeserializeResult.success(null));
		assertThrows(NullPointerException.class, () -> IDeserializeResult.partialSuccess(null, "diagnostic"));
		assertThrows(IllegalArgumentException.class, () -> IDeserializeResult.partialSuccess("value", List.of()));
		assertThrows(IllegalArgumentException.class, () -> IDeserializeResult.failure(List.of()));
		assertThrows(IllegalArgumentException.class, () -> IDeserializeResult.failure(" "));
	}

	private static <T> T deserializeValue(IConfigValueSerializer<T> serializer, String string) {
		IDeserializeResult<T> result = serializer.deserialize(string);
		assertEquals(List.of(), result.getDiagnostics());
		return result.getResult().orElseThrow();
	}

	private enum TestEnum {
		FIRST_VALUE,
		SECOND_VALUE
	}

	private record NamedColor(String name, PackedColor color) {}

	private static final class NamedColorSerializer implements IConfigKeyValueSerializer<NamedColor, String, PackedColor> {
		@Override
		public IConfigValueSerializer<String> getKeySerializer() {
			return StringSerializer.INSTANCE;
		}

		@Override
		public IConfigValueSerializer<PackedColor> getValueSerializer() {
			return ColorSerializer.INSTANCE;
		}

		@Override
		public String getKey(NamedColor entry) {
			return entry.name();
		}

		@Override
		public PackedColor getValue(NamedColor entry) {
			return entry.color();
		}

		@Override
		public NamedColor createEntry(String key, PackedColor value) {
			return new NamedColor(key, value);
		}

		@Override
		public String serialize(NamedColor value) {
			return "%s:%06X".formatted(value.name(), value.color().packedValue());
		}

		@Override
		public IDeserializeResult<NamedColor> deserialize(String string) {
			String[] parts = string.split(":", 2);
			if (parts.length != 2 || parts[0].isBlank() || parts[1].length() != 6) {
				return IDeserializeResult.failure("Named colors must contain a name and RGB value separated by ':'");
			}
			try {
				int color = Integer.parseInt(parts[1], 16);
				return IDeserializeResult.success(new NamedColor(parts[0], PackedColor.rgb(color)));
			} catch (NumberFormatException e) {
				return IDeserializeResult.failure("Named colors must contain an RGB hex value");
			}
		}

		@Override
		public boolean isValid(NamedColor value) {
			return !value.name().isBlank() && value.color().format() == ConfigColorFormat.RGB;
		}

		@Override
		public String getValidValuesDescription() {
			return "A name and RGB hex color separated by ':'";
		}
	}
}
