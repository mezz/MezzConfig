package net.mezzdev.config.serializers;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.value.ConfigListOrdering;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serializer for comma-separated list config values.
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
		return values.stream()
			.map(elementSerializer::serialize)
			.collect(Collectors.joining(", "));
	}

	@Override
	public DeserializeResult<List<T>> deserialize(String string) {
		string = string.trim();
		if (string.startsWith("[")) {
			IDeserializeResult<JsonElement> decodeResult = ConfigFileValueCodec.deserialize(string);
			JsonElement value = decodeResult.getResult().orElse(null);
			if (value == null) {
				if (string.endsWith("]")) {
					return deserializeCommaSeparated(string.substring(1, string.length() - 1));
				}
				return DeserializeResult.failure(decodeResult.getDiagnostics());
			}
			return copyResult(ConfigFileValueAdapter.deserialize(this, value));
		}
		return deserializeCommaSeparated(string);
	}

	private DeserializeResult<List<T>> deserializeCommaSeparated(String string) {
		String[] split = string.split(",");

		List<String> diagnostics = new ArrayList<>();
		List<T> results = Arrays.stream(split)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(elementSerializer::deserialize)
			.<T>mapMulti((r, c) -> {
				r.getResult().ifPresent(c);
				diagnostics.addAll(r.getDiagnostics());
			})
			.toList();

		if (diagnostics.isEmpty()) {
			return DeserializeResult.success(results);
		}
		if (results.isEmpty()) {
			return DeserializeResult.failure(diagnostics);
		}
		return DeserializeResult.partialSuccess(results, diagnostics);
	}

	private static <T> DeserializeResult<T> copyResult(IDeserializeResult<T> result) {
		return switch (result.getState()) {
			case SUCCESS -> DeserializeResult.success(result.getResult().orElseThrow());
			case PARTIAL_SUCCESS -> DeserializeResult.partialSuccess(result.getResult().orElseThrow(), result.getDiagnostics());
			case FAILURE -> DeserializeResult.failure(result.getDiagnostics());
		};
	}

	@Override
	public String getValidValuesDescription() {
		return "A comma-separated list containing values of:\n%s".formatted(elementSerializer.getValidValuesDescription());
	}

	@Override
	public boolean isValid(List<T> value) {
		return value.stream()
			.allMatch(elementSerializer::isValid);
	}
}
