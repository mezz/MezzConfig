package net.mezzdev.config.serializers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.mezzdev.config.api.value.serializer.ConfigListOrdering;
import net.mezzdev.config.api.value.serializer.IConfigListValueSerializer;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.util.ErrorUtil;

import java.util.List;
import java.util.Optional;

/**
 * Serializer for structured list config values.
 */
public final class ListSerializer<T> implements IConfigListValueSerializer<T> {
	private final IConfigValueSerializer<T> elementSerializer;
	private final ConfigListOrdering ordering;

	public ListSerializer(IConfigValueSerializer<T> elementSerializer) {
		this(elementSerializer, ConfigListOrdering.ORDERED);
	}

	public ListSerializer(IConfigValueSerializer<T> elementSerializer, ConfigListOrdering ordering) {
		this.elementSerializer = ErrorUtil.checkNotNull(elementSerializer, "elementSerializer");
		this.ordering = ErrorUtil.checkNotNull(ordering, "ordering");
	}

	@Override
	public ConfigListOrdering getOrdering() {
		return ordering;
	}

	@Override
	public IConfigValueSerializer<T> getElementSerializer() {
		return elementSerializer;
	}

	@Override
	public String serialize(List<T> values) {
		return ConfigFileValueAdapter.serialize(this, values);
	}

	@Override
	public DeserializeResult<List<T>> deserialize(String string) {
		IDeserializeResult<JsonElement> decodeResult = ConfigFileValueCodec.deserialize(string);
		JsonElement value = decodeResult.getResult().orElse(null);
		if (value == null) {
			return DeserializeResult.failure(decodeResult.getDiagnostics());
		}
		if (!(value instanceof JsonArray)) {
			return DeserializeResult.failure("Expected a structured array.");
		}
		return copyResult(ConfigFileValueAdapter.deserialize(this, value));
	}

	private static <T> DeserializeResult<T> copyResult(IDeserializeResult<T> result) {
		Optional<T> value = result.getResult();
		if (value.isEmpty()) {
			return DeserializeResult.failure(result.getDiagnostics());
		}
		if (result.getDiagnostics().isEmpty()) {
			return DeserializeResult.success(value.orElseThrow());
		}
		return DeserializeResult.partialSuccess(value.orElseThrow(), result.getDiagnostics());
	}

	@Override
	public String getValidValuesDescription() {
		return "A list containing values of:\n%s".formatted(elementSerializer.getValidValuesDescription());
	}

	@Override
	public boolean isValid(List<T> value) {
		return value.stream()
			.allMatch(elementSerializer::isValid);
	}
}
