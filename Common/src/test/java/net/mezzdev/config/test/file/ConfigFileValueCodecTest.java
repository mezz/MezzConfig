package net.mezzdev.config.test.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigFileValueCodecTest {
	@Test
	public void scalarRoundTripsPlainAndDelimiterSensitiveText() {
		// Setup: scalar values cover empty, padded, delimiter-sensitive, escaped, Unicode, and multiline text.
		List<String> values = List.of(
			"plain-value",
			"",
			" surrounding ",
			"a,b = c # text [section]",
			"\"quotes\", /slashes/, and \\backslashes\\",
			"こんにちは 🌍",
			"line one\nline two\r\n"
		);

		// Operation and assertions: every value survives scalar encoding and decoding unchanged.
		for (String expected : values) {
			String encoded = ConfigFileValueCodec.serializeScalar(expected);
			String decoded = deserializeScalar(encoded);

			assertEquals(expected, decoded);
		}
	}

	@Test
	public void arraysPreserveOrderEmptyValuesAndNestedBoundaries() {
		// Setup: an array combines empty, delimiter-sensitive, padded, and nested values.
		JsonArray nested = new JsonArray();
		nested.add("nested");
		JsonArray value = new JsonArray();
		value.add("");
		value.add("a,b");
		value.add(" surrounding ");
		value.add(nested);

		// Operation: serialize the complete array.
		String encoded = ConfigFileValueCodec.serialize(value);

		// Assertions: encoding is canonical and decoding preserves the original array structure.
		assertEquals("[\"\",\"a,b\",\" surrounding \",[\"nested\"]]", encoded);
		assertEquals(value, deserialize(encoded));
	}

	@Test
	public void reportsMalformedQuotedValuesAndArraysAsDeserializeFailures() {
		// Setup: quoted and array encodings are malformed in several distinct ways.
		List<String> malformedValues = List.of(
			"\"unterminated",
			"\"bad\\q\"",
			"[first,,second]",
			"[first,]",
			"[] trailing"
		);

		// Operation and assertions: every malformed encoding returns one useful failure diagnostic.
		malformedValues.forEach(ConfigFileValueCodecTest::assertFailure);
	}

	@Test
	public void supportsJsonSurrogatePairsAndRejectsUnpairedSurrogates() {
		// Setup: JSON escapes can contain either a valid surrogate pair or malformed unpaired values.
		String validPair = "\"\\uD83C\\uDF0D\"";
		List<String> malformedValues = List.of("\"\\uD800\"", "\uD800", "\"\\U00110000\"");

		// Operation and assertions: the valid pair decodes while malformed input and output are rejected.
		assertEquals("🌍", deserializeScalar(validPair));
		malformedValues.forEach(ConfigFileValueCodecTest::assertFailure);
		assertThrows(IllegalArgumentException.class, () -> ConfigFileValueCodec.serializeScalar("\uD800"));
	}

	private static JsonElement deserialize(String encoded) {
		IDeserializeResult<JsonElement> result = ConfigFileValueCodec.deserialize(encoded);
		return result.getResult().orElseThrow(() -> new AssertionError(result.getDiagnostics()));
	}

	private static String deserializeScalar(String encoded) {
		IDeserializeResult<String> result = ConfigFileValueCodec.deserializeScalar(encoded);
		return result.getResult().orElseThrow(() -> new AssertionError(result.getDiagnostics()));
	}

	private static void assertFailure(String encoded) {
		IDeserializeResult<JsonElement> result = ConfigFileValueCodec.deserialize(encoded);
		assertTrue(result.getResult().isEmpty());
		assertEquals(1, result.getDiagnostics().size());
		assertTrue(result.getDiagnostics().stream().noneMatch(String::isBlank));
	}
}
