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
		// Operation and assertions: booleans parse without case or padding sensitivity and expose their finite domain.
		assertEquals(true, deserializeValue(BooleanSerializer.INSTANCE, " TRUE "));
		assertEquals(false, deserializeValue(BooleanSerializer.INSTANCE, " false "));
		assertEquals(List.of(true, false), List.copyOf(BooleanSerializer.INSTANCE.getAllValidValues().orElseThrow()));
	}

	@Test
	public void booleanSerializerRejectsInvalidValues() {
		// Operation: try to deserialize text outside the boolean domain.
		IDeserializeResult<Boolean> result = BooleanSerializer.INSTANCE.deserialize("enabled");

		// Assertions: failure has no value and explains the accepted literals.
		assertTrue(result.getResult().isEmpty());
		assertEquals(List.of("string must be 'true' or 'false'"), result.getDiagnostics());
	}

	@Test
	public void stringSerializerPreservesValues() {
		// Operation and assertions: strings round-trip verbatim, including empty values, with null as the only invalid value.
		assertEquals("hello world", deserializeValue(StringSerializer.INSTANCE, "hello world"));
		assertEquals("", deserializeValue(StringSerializer.INSTANCE, ""));
		assertEquals("hello world", StringSerializer.INSTANCE.serialize("hello world"));
		assertTrue(StringSerializer.INSTANCE.isValid(""));
		assertFalse(StringSerializer.INSTANCE.isValid(null));
	}

	@Test
	public void integerSerializerParsesAndValidatesBounds() {
		// Setup: an integer serializer has a small inclusive range.
		IntegerSerializer serializer = new IntegerSerializer(2, 4);

		// Operation and assertions: boundaries parse, range validation holds, and the finite domain is discoverable.
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
		// Setup: an integer serializer accepts only values from two through four.
		IntegerSerializer serializer = new IntegerSerializer(2, 4);

		// Operation: deserialize an out-of-range number and non-numeric text.
		IDeserializeResult<Integer> outOfRange = serializer.deserialize("5");
		IDeserializeResult<Integer> notAnInteger = serializer.deserialize("five");

		// Assertions: both fail with diagnostics that distinguish range and parsing errors.
		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid integer. Must be: An integer in the range [2, 4] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notAnInteger.getResult().isEmpty());
		assertTrue(notAnInteger.getDiagnostics().getFirst().contains("Unable to parse int: 'five'"));
	}

	@Test
	public void integerSerializerOmitsAllValidValuesForLargeRanges() {
		// Setup: an integer range is too large to enumerate usefully.
		IntegerSerializer serializer = new IntegerSerializer(0, 20);

		// Operation and assertions: the serializer omits an exhaustive valid-values collection.
		assertTrue(serializer.getAllValidValues().isEmpty());
	}

	@Test
	public void colorSerializerParsesAndSerializesRgbAndArgbHexColors() {
		// Setup: packed colors cover opaque RGB and alpha-bearing ARGB formats.
		PackedColor rgb = PackedColor.rgb(0x112233);
		PackedColor argb = PackedColor.argb(0x80112233);

		// Operation and assertions: canonical text round-trips both formats case-insensitively and rejects null.
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
		// Operation and assertions: constructing RGB with alpha data is rejected before information can be lost.
		assertThrows(IllegalArgumentException.class, () -> PackedColor.rgb(0xFF112233));
	}

	@Test
	public void colorSerializerRejectsInvalidColors() {
		// Operation: deserialize colors with a missing prefix, wrong length, bad hex, and invalid quoting.
		IDeserializeResult<PackedColor> missingPrefix = ColorSerializer.INSTANCE.deserialize("FF112233");
		IDeserializeResult<PackedColor> shortColor = ColorSerializer.INSTANCE.deserialize("0x12345");
		IDeserializeResult<PackedColor> invalidHex = ColorSerializer.INSTANCE.deserialize("0xGG112233");
		IDeserializeResult<PackedColor> quotedColor = ColorSerializer.INSTANCE.deserialize("\"0x112233\"");

		// Assertions: every malformed form fails, with stable validation and parsing diagnostics.
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
		// Setup: a long serializer has a small inclusive range.
		LongSerializer serializer = new LongSerializer(2L, 4L);

		// Operation and assertions: boundaries parse, range validation holds, and the finite domain is discoverable.
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
		// Setup: a long serializer accepts only values from two through four.
		LongSerializer serializer = new LongSerializer(2L, 4L);

		// Operation: deserialize an out-of-range number and non-numeric text.
		IDeserializeResult<Long> outOfRange = serializer.deserialize("5");
		IDeserializeResult<Long> notALong = serializer.deserialize("five");

		// Assertions: both fail with diagnostics that distinguish range and parsing errors.
		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid long. Must be: A long in the range [2, 4] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notALong.getResult().isEmpty());
		assertTrue(notALong.getDiagnostics().getFirst().contains("Unable to parse long: 'five'"));
	}

	@Test
	public void longSerializerOmitsAllValidValuesForLargeRanges() {
		// Setup: a long range is too large to enumerate usefully.
		LongSerializer serializer = new LongSerializer(0, 20);

		// Operation and assertions: the serializer omits an exhaustive valid-values collection.
		assertTrue(serializer.getAllValidValues().isEmpty());
	}

	@Test
	public void doubleSerializerParsesAndValidatesBounds() {
		// Setup: a double serializer has finite inclusive bounds.
		DoubleSerializer serializer = new DoubleSerializer(0.5, 2.5);

		// Operation and assertions: bounds parse and validate while out-of-range and non-finite values do not.
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
		// Setup: a double serializer accepts finite values from 0.5 through 2.5.
		DoubleSerializer serializer = new DoubleSerializer(0.5, 2.5);

		// Operation: deserialize an out-of-range number and non-numeric text.
		IDeserializeResult<Double> outOfRange = serializer.deserialize("3.0");
		IDeserializeResult<Double> notADouble = serializer.deserialize("many");

		// Assertions: both fail with diagnostics that distinguish range and parsing errors.
		assertTrue(outOfRange.getResult().isEmpty());
		assertEquals(List.of("Invalid double. Must be: A finite double in the range [0.5, 2.5] (inclusive)"), outOfRange.getDiagnostics());
		assertTrue(notADouble.getResult().isEmpty());
		assertTrue(notADouble.getDiagnostics().getFirst().contains("Unable to parse double: 'many'"));
	}

	@Test
	public void enumSerializerSerializesNamesAndReportsValidValues() {
		// Setup: an enum serializer accepts every constant of its type.
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class);

		// Operation and assertions: names round-trip and both descriptive and structured domains preserve declaration order.
		assertEquals("FIRST_VALUE", serializer.serialize(TestEnum.FIRST_VALUE));
		assertEquals(TestEnum.FIRST_VALUE, deserializeValue(serializer, "FIRST_VALUE"));
		assertEquals("[FIRST_VALUE, SECOND_VALUE]", serializer.getValidValuesDescription());
		assertEquals(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE), List.copyOf(serializer.getAllValidValues().orElseThrow()));
	}

	@Test
	public void enumSerializerRejectsInvalidNames() {
		// Setup: an enum serializer expects raw declared names.
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class);

		// Operation: deserialize an unknown name and a quoted known name.
		IDeserializeResult<TestEnum> result = serializer.deserialize("MISSING");
		IDeserializeResult<TestEnum> quotedResult = serializer.deserialize("\"FIRST_VALUE\"");

		// Assertions: both fail and the unknown-name diagnostic explains the enum contract.
		assertTrue(result.getResult().isEmpty());
		assertTrue(result.getDiagnostics().getFirst().contains("Invalid enum name"));
		assertTrue(quotedResult.getResult().isEmpty());
	}

	@Test
	public void enumSerializerSupportsRestrictedValidValues() {
		// Setup: an enum serializer exposes only the second constant.
		EnumSerializer<TestEnum> serializer = new EnumSerializer<>(TestEnum.class, List.of(TestEnum.SECOND_VALUE));

		// Operation and assertions: the restricted value works everywhere and excluded constants are invalid.
		assertEquals(TestEnum.SECOND_VALUE, deserializeValue(serializer, "SECOND_VALUE"));
		assertEquals("[SECOND_VALUE]", serializer.getValidValuesDescription());
		assertEquals(List.of(TestEnum.SECOND_VALUE), List.copyOf(serializer.getAllValidValues().orElseThrow()));
		assertFalse(serializer.isValid(TestEnum.FIRST_VALUE));
		assertTrue(serializer.deserialize("FIRST_VALUE").getDiagnostics().getFirst().contains("Invalid enum name"));
	}

	@Test
	public void enumSerializerRejectsInvalidValidValueLists() {
		// Operation and assertions: an enum restriction must be non-empty and contain no duplicate constants.
		assertThrows(IllegalArgumentException.class, () -> new EnumSerializer<>(TestEnum.class, List.of()));
		assertThrows(IllegalArgumentException.class, () -> new EnumSerializer<>(TestEnum.class, List.of(TestEnum.FIRST_VALUE, TestEnum.FIRST_VALUE)));
	}

	@Test
	public void listSerializerDeserializesStructuredValues() {
		// Setup: a list serializer wraps the built-in boolean serializer.
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		// Operation and assertions: structured arrays parse, serialize canonically, and validate as complete lists.
		assertEquals(List.of(true, false, true), deserializeValue(serializer, "[\"true\",\"false\",\"TRUE\"]"));
		assertEquals(List.of(), deserializeValue(serializer, "[]"));
		assertEquals("[\"true\",\"false\"]", serializer.serialize(List.of(true, false)));
		assertTrue(serializer.isValid(List.of(true, false)));
	}

	@Test
	public void listSerializerRejectsMissingClosingBracket() {
		// Setup: a structured boolean array is missing its closing bracket.
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		// Operation: deserialize the malformed array.
		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[true, false");

		// Assertions: structured parsing fails once with a JSON diagnostic.
		assertTrue(result.getResult().isEmpty());
		assertEquals(1, result.getDiagnostics().size());
		assertTrue(result.getDiagnostics().getFirst().startsWith("Invalid JSON value"));
	}

	@Test
	public void listSerializerReportsPartialSuccessForRecoveredElements() {
		// Setup: a structured boolean array contains valid entries around one invalid element.
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		// Operation: deserialize the partially recoverable array.
		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[\"true\",\"invalid\",\"false\"]");

		// Assertions: valid elements are returned with an indexed diagnostic for the omitted element.
		assertEquals(List.of(true, false), result.getResult().orElseThrow());
		assertEquals(List.of("Array element 1: string must be 'true' or 'false'"), result.getDiagnostics());
	}

	@Test
	public void listSerializerReportsFailureWhenNoElementsAreRecovered() {
		// Setup: every element in a structured boolean array is invalid.
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		// Operation: deserialize the wholly unrecoverable array.
		IDeserializeResult<List<Boolean>> result = serializer.deserialize("[\"invalid\",\"also-invalid\"]");

		// Assertions: no partial value is returned and each failed index has a diagnostic.
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
		// Setup: comma-separated booleans are supplied without a structured array wrapper.
		ListSerializer<Boolean> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);

		// Operation: deserialize the unstructured text.
		IDeserializeResult<List<Boolean>> result = serializer.deserialize("true, false");

		// Assertions: list parsing fails with the required storage shape.
		assertTrue(result.getResult().isEmpty());
		assertEquals(List.of("Expected a structured array."), result.getDiagnostics());
	}

	@Test
	public void listSerializerWrapsElementSerializer() {
		// Setup: ordered and unordered list serializers wrap the same boolean element serializer.
		IConfigValueSerializer<List<Boolean>> serializer = new ListSerializer<>(BooleanSerializer.INSTANCE);
		IConfigListValueSerializer<Boolean> unorderedSerializer = new ListSerializer<>(
			BooleanSerializer.INSTANCE,
			ConfigListOrdering.UNORDERED
		);

		// Operation and assertions: values round-trip while list metadata exposes the wrapped serializer and ordering.
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
		// Setup: a list serializer wraps a finite enum serializer.
		ListSerializer<TestEnum> serializer = new ListSerializer<>(new EnumSerializer<>(TestEnum.class));

		// Operation and assertions: enum names round-trip and their valid-values description is composed for the list.
		assertEquals(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE), deserializeValue(serializer, "[\"FIRST_VALUE\",\"SECOND_VALUE\"]"));
		assertEquals("[\"FIRST_VALUE\",\"SECOND_VALUE\"]", serializer.serialize(List.of(TestEnum.FIRST_VALUE, TestEnum.SECOND_VALUE)));
		assertEquals("A list containing values of:\n[FIRST_VALUE, SECOND_VALUE]", serializer.getValidValuesDescription());
	}

	@Test
	public void listSerializerReadsLosslessStructuredStrings() {
		// Setup: structured strings include empty, comma-sensitive, padded, bracketed, multiline, and escaped values.
		ListSerializer<String> serializer = new ListSerializer<>(StringSerializer.INSTANCE);
		List<String> expected = List.of("", "a,b", " surrounding ", "[section]", "line one\nline two", "\\path");
		String encoded = "[\"\", \"a,b\", \" surrounding \", \"[section]\", \"line one\\nline two\", \"\\\\path\"]";

		// Operation and assertions: external and serializer-produced encodings both preserve every string exactly.
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
		// Operation: create successful, failed, and partially successful deserialize results.
		IDeserializeResult<String> success = IDeserializeResult.success("value");
		IDeserializeResult<String> failure = IDeserializeResult.failure("error");
		IDeserializeResult<String> partial = IDeserializeResult.partialSuccess("partial", List.of("warning"));

		// Assertions: each factory populates values and diagnostics according to its result state.
		assertEquals("value", success.getResult().orElseThrow());
		assertEquals(List.of(), success.getDiagnostics());
		assertTrue(failure.getResult().isEmpty());
		assertEquals(List.of("error"), failure.getDiagnostics());
		assertEquals("partial", partial.getResult().orElseThrow());
		assertEquals(List.of("warning"), partial.getDiagnostics());
	}

	@Test
	public void deserializeResultFactoriesRejectInvalidStates() {
		// Operation and assertions: result factories reject null values and missing or blank diagnostics.
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
