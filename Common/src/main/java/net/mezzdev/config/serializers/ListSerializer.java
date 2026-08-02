package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serializer for comma-separated list config values.
 */
public final class ListSerializer<T> implements IConfigListValueSerializer<T> {
	private final IConfigValueSerializer<T> elementSerializer;

	public ListSerializer(IConfigValueSerializer<T> elementSerializer) {
		this.elementSerializer = ErrorUtil.checkNotNull(elementSerializer, "elementSerializer");
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
			if (!string.endsWith("]")) {
				String errorMessage = """
					No closing brace found.
					List must have no braces, or be wrapped in [ and ].""";
				return new DeserializeResult<>(null, errorMessage);
			}
			string = string.substring(1, string.length() - 1);
		}
		String[] split = string.split(",");

		List<String> errors = new ArrayList<>();
		List<T> results = Arrays.stream(split)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(elementSerializer::deserialize)
			.<T>mapMulti((r, c) -> {
				r.getResult().ifPresent(c);
				errors.addAll(r.getErrors());
			})
			.toList();

		return new DeserializeResult<>(results, errors);
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

	@Override
	public Optional<Collection<List<T>>> getAllValidValues() {
		return Optional.empty();
	}
}
