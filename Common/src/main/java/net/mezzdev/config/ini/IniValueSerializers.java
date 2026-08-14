package net.mezzdev.config.ini;

import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;

import java.util.ArrayList;
import java.util.List;

public final class IniValueSerializers {
	private static final int MAX_DIAGNOSTICS = 100;

	private IniValueSerializers() {}

	public static <T> IniValue serialize(IConfigValueSerializer<T> serializer, T value) {
		return serializeUnknown(serializer, value);
	}

	private static IniValue serializeUnknown(IConfigValueSerializer<?> serializer, Object value) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer && value instanceof List<?> list) {
			return IniValue.array(list.stream()
				.map(element -> serializeUnknown(listSerializer.getElementSerializer(), element))
				.toList());
		}
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return IniValue.scalar(typedSerializer.serialize(value));
	}

	public static <T> IDeserializeResult<T> deserialize(IConfigValueSerializer<T> serializer, IniValue value) {
		if (serializer instanceof IConfigListValueSerializer<?> listSerializer) {
			return deserializeList(serializer, listSerializer, value);
		}
		if (value instanceof IniValue.Scalar scalar) {
			return serializer.deserialize(scalar.value());
		}
		return IDeserializeResult.failure("Expected a scalar INI value, but found an array.");
	}

	private static <T> IDeserializeResult<T> deserializeList(
		IConfigValueSerializer<T> serializer,
		IConfigListValueSerializer<?> listSerializer,
		IniValue value
	) {
		if (value instanceof IniValue.Scalar scalar) {
			return serializer.deserialize(scalar.value());
		}
		IniValue.Array array = (IniValue.Array) value;
		List<String> diagnostics = new ArrayList<>();
		List<Object> results = new ArrayList<>();
		List<IniValue> elements = array.values();
		for (int index = 0; index < elements.size(); index++) {
			IDeserializeResult<?> result = deserializeUnknown(listSerializer.getElementSerializer(), elements.get(index));
			result.getResult().ifPresent(results::add);
			for (String diagnostic : result.getDiagnostics()) {
				addDiagnostic(diagnostics, "Array element %s: %s".formatted(index, diagnostic));
			}
		}
		IDeserializeResult<List<Object>> listResult;
		if (diagnostics.isEmpty()) {
			listResult = IDeserializeResult.success(List.copyOf(results));
		} else if (results.isEmpty() && !elements.isEmpty()) {
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

	private static IDeserializeResult<?> deserializeUnknown(IConfigValueSerializer<?> serializer, IniValue value) {
		@SuppressWarnings("unchecked")
		IConfigValueSerializer<Object> typedSerializer = (IConfigValueSerializer<Object>) serializer;
		return deserialize(typedSerializer, value);
	}

	public static String toPublicSerializerRepresentation(IniValue value) {
		return switch (value) {
			case IniValue.Scalar scalar -> scalar.value();
			case IniValue.Array array -> IniValueCodec.serialize(array);
		};
	}
}
