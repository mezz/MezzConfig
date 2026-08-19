package net.mezzdev.config.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConfigFileValueAdapter {
	private static final int MAX_DIAGNOSTICS = 100;
	private static final int MAX_DIAGNOSTIC_CHARACTERS = 1_024;

	private ConfigFileValueAdapter() {}

	public static <T> String serialize(IConfigValueSerializer<T> serializer, T value) {
		try {
			return ConfigFileValueCodec.serialize(serializeUnknown(serializer, value));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(
				summarize("Config serializer failed to serialize a value: " + getExceptionMessage(e)),
				e
			);
		}
	}

	private static JsonElement serializeUnknown(IConfigValueSerializer<?> serializer, Object value) {
		if (!isValidUnknown(serializer, value)) {
			throw new IllegalArgumentException("Config serializer rejected a value that MezzConfig was asked to store.");
		}
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer && value instanceof List<?> list) {
			JsonArray array = new JsonArray(list.size());
			list.stream()
				.map(element -> serializeUnknown(listSerializer.getElementSerializer(), element))
				.forEach(array::add);
			return array;
		}
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		String serialized = typedSerializer.serialize(value);
		if (serialized == null) {
			throw new IllegalArgumentException("Config serializer returned null from serialize.");
		}
		return new JsonPrimitive(serialized);
	}

	public static <T> IDeserializeResult<T> deserialize(IConfigValueSerializer<T> serializer, JsonElement value) {
		try {
			return normalizeResult(serializer, deserializeUnchecked(serializer, value));
		} catch (RuntimeException e) {
			return IDeserializeResult.failure(summarize(
				"Config serializer failed to deserialize a value: " + getExceptionMessage(e)
			));
		}
	}

	public static <T> IDeserializeResult<T> deserializeScalar(IConfigValueSerializer<T> serializer, String value) {
		try {
			return normalizeResult(serializer, serializer.deserialize(value));
		} catch (RuntimeException e) {
			return IDeserializeResult.failure(summarize(
				"Config serializer failed to deserialize a value: " + getExceptionMessage(e)
			));
		}
	}

	private static <T> IDeserializeResult<T> deserializeUnchecked(IConfigValueSerializer<T> serializer, JsonElement value) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer) {
			return deserializeList(serializer, listSerializer, value);
		}
		if (value instanceof JsonPrimitive primitive) {
			return serializer.deserialize(primitive.getAsString());
		}
		return IDeserializeResult.failure("Expected a scalar config value.");
	}

	public static <T> void validateRoundTrip(IConfigValueSerializer<T> serializer, T value) {
		String firstSerialized = serializeDirect(serializer, value);
		String secondSerialized = serializeDirect(serializer, value);
		if (!firstSerialized.equals(secondSerialized)) {
			throw new IllegalArgumentException("Config serializer returned different text for the same value.");
		}
		IDeserializeResult<T> directResult = deserializeScalar(serializer, firstSerialized);
		T directValue = requireRoundTripResult(directResult);
		if (!value.equals(directValue)) {
			throw new IllegalArgumentException("Config serializer did not round-trip its value.");
		}

		String storedValue = serialize(serializer, value);
		IDeserializeResult<JsonElement> decodedValue = ConfigFileValueCodec.deserialize(storedValue);
		if (!decodedValue.getDiagnostics().isEmpty() || decodedValue.getResult().isEmpty()) {
			throw new IllegalArgumentException("Config serializer produced an invalid stored representation.");
		}
		T storedRoundTripValue = requireRoundTripResult(deserialize(serializer, decodedValue.getResult().orElseThrow()));
		if (!value.equals(storedRoundTripValue)) {
			throw new IllegalArgumentException("Config serializer did not round-trip its stored value.");
		}
	}

	private static <T> String serializeDirect(IConfigValueSerializer<T> serializer, T value) {
		try {
			String serialized = serializer.serialize(value);
			if (serialized == null) {
				throw new IllegalArgumentException("Config serializer returned null from serialize.");
			}
			return serialized;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(
				summarize("Config serializer failed to serialize a value: " + getExceptionMessage(e)),
				e
			);
		}
	}

	private static <T> T requireRoundTripResult(IDeserializeResult<T> result) {
		if (!result.getDiagnostics().isEmpty() || result.getResult().isEmpty()) {
			String diagnostics = String.join("; ", result.getDiagnostics());
			throw new IllegalArgumentException(summarize(
				"Config serializer could not deserialize its own value: " + diagnostics
			));
		}
		return result.getResult().orElseThrow();
	}

	private static <T> IDeserializeResult<T> normalizeResult(
		IConfigValueSerializer<T> serializer,
		IDeserializeResult<T> result
	) {
		result = Objects.requireNonNull(result, "Config serializer returned null from deserialize.");
		Optional<T> optionalResult = Objects.requireNonNull(result.getResult(), "Deserialization result returned null result state.");
		List<String> rawDiagnostics = Objects.requireNonNull(
			result.getDiagnostics(),
			"Deserialization result returned null diagnostics."
		);
		List<String> diagnostics = new ArrayList<>();
		for (String diagnostic : rawDiagnostics) {
			if (diagnostic == null || diagnostic.isBlank()) {
				addDiagnostic(diagnostics, "Config serializer returned a null or blank diagnostic.");
			} else {
				addDiagnostic(diagnostics, diagnostic);
			}
		}
		if (optionalResult.isEmpty()) {
			if (diagnostics.isEmpty()) {
				addDiagnostic(diagnostics, "Config serializer returned no value or diagnostic.");
			}
			return IDeserializeResult.failure(diagnostics);
		}

		T deserializedValue = optionalResult.orElseThrow();
		if (!serializer.isValid(deserializedValue)) {
			addDiagnostic(diagnostics, "Config serializer returned a value that it reports as invalid.");
			return IDeserializeResult.failure(diagnostics);
		}
		if (diagnostics.isEmpty()) {
			return IDeserializeResult.success(deserializedValue);
		}
		return IDeserializeResult.partialSuccess(deserializedValue, diagnostics);
	}

	private static <T> IDeserializeResult<T> deserializeList(
		IConfigValueSerializer<T> serializer,
		IConfigListValueSerializer<?> listSerializer,
		JsonElement value
	) {
		if (value instanceof JsonPrimitive primitive) {
			return serializer.deserialize(primitive.getAsString());
		}
		if (!(value instanceof JsonArray array)) {
			return IDeserializeResult.failure("Expected a config value array.");
		}
		List<String> diagnostics = new ArrayList<>();
		List<Object> results = new ArrayList<>();
		for (int index = 0; index < array.size(); index++) {
			IDeserializeResult<?> result = deserializeUnknown(listSerializer.getElementSerializer(), array.get(index));
			result.getResult().ifPresent(results::add);
			for (String diagnostic : result.getDiagnostics()) {
				addDiagnostic(diagnostics, "Array element %s: %s".formatted(index, diagnostic));
			}
		}
		List<Object> immutableResults = List.copyOf(results);
		@SuppressWarnings("unchecked")
		T typedResults = (T) immutableResults;
		if (!serializer.isValid(typedResults)) {
			addDiagnostic(diagnostics, "Invalid list value: " + serializer.getValidValuesDescription());
			return IDeserializeResult.failure(diagnostics);
		}

		IDeserializeResult<List<Object>> listResult;
		if (diagnostics.isEmpty()) {
			listResult = IDeserializeResult.success(immutableResults);
		} else if (results.isEmpty() && !array.isEmpty()) {
			listResult = IDeserializeResult.failure(diagnostics);
		} else {
			listResult = IDeserializeResult.partialSuccess(immutableResults, diagnostics);
		}
		@SuppressWarnings("unchecked")
		IDeserializeResult<T> typedResult = (IDeserializeResult<T>) listResult;
		return typedResult;
	}

	private static void addDiagnostic(List<String> diagnostics, String diagnostic) {
		if (diagnostics.size() < MAX_DIAGNOSTICS) {
			diagnostics.add(summarize(diagnostic));
		} else if (diagnostics.size() == MAX_DIAGNOSTICS) {
			diagnostics.add("Further array element diagnostics were suppressed.");
		}
	}

	private static String getExceptionMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}
		return summarize(message);
	}

	private static String summarize(String diagnostic) {
		if (diagnostic.length() <= MAX_DIAGNOSTIC_CHARACTERS) {
			return diagnostic;
		}
		return diagnostic.substring(0, MAX_DIAGNOSTIC_CHARACTERS - 1) + "…";
	}

	private static IDeserializeResult<?> deserializeUnknown(IConfigValueSerializer<?> serializer, JsonElement value) {
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return deserialize(typedSerializer, value);
	}

	private static boolean isValidUnknown(IConfigValueSerializer<?> serializer, Object value) {
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return typedSerializer.isValid(value);
	}

}
