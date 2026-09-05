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
		List<String> values = List.of(
			"plain-value",
			"",
			" surrounding ",
			"a,b = c # text [section]",
			"\"quotes\", /slashes/, and \\backslashes\\",
			"こんにちは 🌍",
			"line one\nline two\r\n"
		);

		for (String expected : values) {
			String encoded = ConfigFileValueCodec.serializeScalar(expected);
			String decoded = deserializeScalar(encoded);

			assertEquals(expected, decoded);
		}
	}

	@Test
	public void arraysPreserveOrderEmptyValuesAndNestedBoundaries() {
		JsonArray nested = new JsonArray();
		nested.add("nested");
		JsonArray value = new JsonArray();
		value.add("");
		value.add("a,b");
		value.add(" surrounding ");
		value.add(nested);

		String encoded = ConfigFileValueCodec.serialize(value);

		assertEquals("[\"\",\"a,b\",\" surrounding \",[\"nested\"]]", encoded);
		assertEquals(value, deserialize(encoded));
	}

	@Test
	public void reportsMalformedQuotedValuesAndArraysAsDeserializeFailures() {
		assertFailure("\"unterminated");
		assertFailure("\"bad\\q\"");
		assertFailure("[first,,second]");
		assertFailure("[first,]");
		assertFailure("[] trailing");
	}

	@Test
	public void supportsJsonSurrogatePairsAndRejectsUnpairedSurrogates() {
		assertEquals("🌍", deserializeScalar("\"\\uD83C\\uDF0D\""));
		assertFailure("\"\\uD800\"");
		assertFailure("\uD800");
		assertThrows(IllegalArgumentException.class, () -> ConfigFileValueCodec.serializeScalar("\uD800"));
		assertFailure("\"\\U00110000\"");
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
