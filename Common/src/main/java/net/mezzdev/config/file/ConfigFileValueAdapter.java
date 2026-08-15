package net.mezzdev.config.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;

import java.util.ArrayList;
import java.util.List;

public final class ConfigFileValueAdapter {
	private static final int MAX_DIAGNOSTICS = 100;

	private ConfigFileValueAdapter() {}

	public static <T> String serialize(IConfigValueSerializer<T> serializer, T value) {
		return ConfigFileValueCodec.serialize(serializeUnknown(serializer, value));
	}

	private static JsonElement serializeUnknown(IConfigValueSerializer<?> serializer, Object value) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer && value instanceof List<?> list) {
			JsonArray array = new JsonArray(list.size());
			list.stream()
				.map(element -> serializeUnknown(listSerializer.getElementSerializer(), element))
				.forEach(array::add);
			return array;
		}
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return new JsonPrimitive(typedSerializer.serialize(value));
	}

	public static <T> IDeserializeResult<T> deserialize(IConfigValueSerializer<T> serializer, JsonElement value) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer) {
			return deserializeList(serializer, listSerializer, value);
		}
		if (value instanceof JsonPrimitive primitive) {
			return serializer.deserialize(primitive.getAsString());
		}
		return IDeserializeResult.failure("Expected a scalar config value.");
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
		IDeserializeResult<List<Object>> listResult;
		if (diagnostics.isEmpty()) {
			listResult = IDeserializeResult.success(List.copyOf(results));
		} else if (results.isEmpty() && !array.isEmpty()) {
			listResult = IDeserializeResult.failure(diagnostics);
		} else {
			listResult = IDeserializeResult.partialSuccess(List.copyOf(results), diagnostics);
		}
		@SuppressWarnings("unchecked")
		IDeserializeResult<T> typedResult = (IDeserializeResult<T>) listResult;
		return typedResult;
	}

	private static void addDiagnostic(List<String> diagnostics, String diagnostic) {
		if (diagnostics.size() < MAX_DIAGNOSTICS) {
			diagnostics.add(diagnostic);
		} else if (diagnostics.size() == MAX_DIAGNOSTICS) {
			diagnostics.add("Further array element diagnostics were suppressed.");
		}
	}

	private static IDeserializeResult<?> deserializeUnknown(IConfigValueSerializer<?> serializer, JsonElement value) {
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return deserialize(typedSerializer, value);
	}

	public static String toPublicSerializerRepresentation(JsonElement value) {
		if (value instanceof JsonPrimitive primitive) {
			return primitive.getAsString();
		}
		return ConfigFileValueCodec.serialize(value);
	}
}
