package net.mezzdev.config.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class ConfigFileValueCodec {
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.create();
	private static final Pattern SAFE_UNQUOTED_VALUE = Pattern.compile("[A-Za-z0-9_.:+/\\-]+");
	private static final int MAX_ARRAY_NESTING = 32;
	private static final int MAX_VALUES = 100_000;

	private ConfigFileValueCodec() {}

	public static String serialize(JsonElement value) {
		if (value instanceof JsonPrimitive primitive) {
			return serializeScalar(primitive.getAsString());
		}
		if (value instanceof JsonArray array) {
			checkValidArray(array);
			return GSON.toJson(array);
		}
		throw new IllegalArgumentException("Config file values must be JSON primitives or arrays.");
	}

	public static String serializeScalar(String value) {
		checkValidUnicode(value);
		if (SAFE_UNQUOTED_VALUE.matcher(value).matches()) {
			return value;
		}
		return GSON.toJson(value);
	}

	private static void checkValidArray(JsonArray array) {
		for (JsonElement element : array) {
			if (element instanceof JsonPrimitive primitive) {
				checkValidUnicode(primitive.getAsString());
			} else if (element instanceof JsonArray nestedArray) {
				checkValidArray(nestedArray);
			} else {
				throw new IllegalArgumentException("Config file arrays may only contain JSON primitives or arrays.");
			}
		}
	}

	public static IDeserializeResult<JsonElement> deserialize(String input) {
		String stripped = input.strip();
		if (stripped.isEmpty()) {
			return IDeserializeResult.failure("Empty values must be written as a JSON string.");
		}
		char first = stripped.charAt(0);
		if (first != '"' && first != '[') {
			if (!isValidUnicode(stripped)) {
				return IDeserializeResult.failure("Unquoted values must contain valid Unicode without unpaired UTF-16 surrogates.");
			}
			if (stripped.chars().anyMatch(ConfigFileValueCodec::isForbiddenControl)) {
				return IDeserializeResult.failure("Unquoted values may not contain control characters.");
			}
			return IDeserializeResult.success(new JsonPrimitive(stripped));
		}

		JsonValueReader reader = new JsonValueReader(stripped);
		return reader.deserialize();
	}

	public static IDeserializeResult<String> deserializeScalar(String input) {
		IDeserializeResult<JsonElement> result = deserialize(input);
		JsonElement value = result.getResult().orElse(null);
		if (value == null) {
			return IDeserializeResult.failure(result.getDiagnostics());
		}
		if (value instanceof JsonPrimitive primitive) {
			return IDeserializeResult.success(primitive.getAsString());
		}
		return IDeserializeResult.failure("Expected a scalar value, not an array.");
	}

	private static void checkValidUnicode(String value) {
		if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
			throw new IllegalArgumentException("Config file values must contain valid Unicode without unpaired UTF-16 surrogates.");
		}
	}

	private static boolean isValidUnicode(String value) {
		return StandardCharsets.UTF_8.newEncoder().canEncode(value);
	}

	private static boolean isForbiddenControl(int character) {
		return character < 0x20 && character != '\t' || character == 0x7F;
	}

	private static final class JsonValueReader {
		private final JsonReader reader;
		private int valueCount;
		private @Nullable String diagnostic;

		private JsonValueReader(String input) {
			this.reader = new JsonReader(new StringReader(input));
			this.reader.setLenient(false);
		}

		private IDeserializeResult<JsonElement> deserialize() {
			try {
				JsonElement value = deserializeValue(0);
				if (value == null) {
					return IDeserializeResult.failure(getDiagnostic());
				}
				if (reader.peek() != JsonToken.END_DOCUMENT) {
					return IDeserializeResult.failure("Unexpected text after the JSON value at " + reader.getPath() + ".");
				}
				return IDeserializeResult.success(value);
			} catch (IOException | IllegalStateException e) {
				return IDeserializeResult.failure("Invalid JSON value: " + e.getMessage());
			}
		}

		private @Nullable JsonElement deserializeValue(int nesting) throws IOException {
			valueCount++;
			if (valueCount > MAX_VALUES) {
				fail("Value exceeds the maximum supported element count of " + MAX_VALUES + ".");
				return null;
			}
			return switch (reader.peek()) {
				case STRING -> deserializeString();
				case BOOLEAN -> new JsonPrimitive(Boolean.toString(reader.nextBoolean()));
				case NUMBER -> new JsonPrimitive(reader.nextString());
				case BEGIN_ARRAY -> deserializeArray(nesting);
				default -> {
					fail("Expected a JSON string, boolean, number, or array at " + reader.getPath() + ".");
					yield null;
				}
			};
		}

		private @Nullable JsonElement deserializeString() throws IOException {
			String value = reader.nextString();
			if (!isValidUnicode(value)) {
				fail("JSON strings must contain valid Unicode without unpaired UTF-16 surrogates at " + reader.getPreviousPath() + ".");
				return null;
			}
			return new JsonPrimitive(value);
		}

		private @Nullable JsonElement deserializeArray(int nesting) throws IOException {
			if (nesting >= MAX_ARRAY_NESTING) {
				fail("Arrays may not be nested more than %s levels at %s.".formatted(MAX_ARRAY_NESTING, reader.getPath()));
				return null;
			}
			reader.beginArray();
			JsonArray values = new JsonArray();
			while (reader.hasNext()) {
				JsonElement value = deserializeValue(nesting + 1);
				if (value == null) {
					return null;
				}
				values.add(value);
			}
			reader.endArray();
			return values;
		}

		private void fail(String diagnostic) {
			if (this.diagnostic == null) {
				this.diagnostic = diagnostic;
			}
		}

		private String getDiagnostic() {
			if (diagnostic == null) {
				return "Unable to deserialize JSON value.";
			}
			return diagnostic;
		}
	}
}
