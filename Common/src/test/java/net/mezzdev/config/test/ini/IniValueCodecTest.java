package net.mezzdev.config.test.ini;

import net.mezzdev.config.ini.IniValue;
import net.mezzdev.config.ini.IniValueCodec;
import net.mezzdev.config.api.value.IDeserializeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IniValueCodecTest {
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
			String encoded = IniValueCodec.serializeScalar(expected);
			IniValue.Scalar decoded = deserializeScalar(encoded);

			assertEquals(expected, decoded.value());
		}
	}

	@Test
	public void arraysPreserveOrderEmptyValuesAndNestedBoundaries() {
		IniValue value = IniValue.array(List.of(
			IniValue.scalar(""),
			IniValue.scalar("a,b"),
			IniValue.scalar(" surrounding "),
			IniValue.array(List.of(IniValue.scalar("nested")))
		));

		String encoded = IniValueCodec.serialize(value);

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
		assertEquals("🌍", deserializeScalar("\"\\uD83C\\uDF0D\"").value());
		assertFailure("\"\\uD800\"");
		assertThrows(IllegalArgumentException.class, () -> IniValueCodec.serializeScalar("\uD800"));
		assertFailure("\"\\U00110000\"");
	}

	private static IniValue deserialize(String encoded) {
		IDeserializeResult<IniValue> result = IniValueCodec.deserialize(encoded);
		return result.getResult().orElseThrow(() -> new AssertionError(result.getDiagnostics()));
	}

	private static IniValue.Scalar deserializeScalar(String encoded) {
		IDeserializeResult<IniValue.Scalar> result = IniValueCodec.deserializeScalar(encoded);
		return result.getResult().orElseThrow(() -> new AssertionError(result.getDiagnostics()));
	}

	private static void assertFailure(String encoded) {
		IDeserializeResult<IniValue> result = IniValueCodec.deserialize(encoded);
		assertTrue(result.getResult().isEmpty());
		assertEquals(1, result.getDiagnostics().size());
		assertTrue(result.getDiagnostics().stream().noneMatch(String::isBlank));
	}
}
