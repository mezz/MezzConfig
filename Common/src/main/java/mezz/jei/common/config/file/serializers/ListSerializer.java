package mezz.jei.common.config.file.serializers;

import mezz.jei.api.runtime.config.IJeiConfigListValueSerializer;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ListSerializer<T> implements IJeiConfigListValueSerializer<T> {
	private final IJeiConfigValueSerializer<T> valueSerializer;
	private final boolean orderSensitive;
	private final boolean flagSet;

	public ListSerializer(IJeiConfigValueSerializer<T> valueSerializer) {
		this(valueSerializer, false, false);
	}

	private ListSerializer(IJeiConfigValueSerializer<T> valueSerializer, boolean orderSensitive, boolean flagSet) {
		if (orderSensitive && flagSet) {
			throw new IllegalArgumentException("A list cannot be both order-sensitive and a flag set");
		}
		this.valueSerializer = valueSerializer;
		this.orderSensitive = orderSensitive;
		this.flagSet = flagSet;
	}

	public static <T> ListSerializer<T> ordered(IJeiConfigValueSerializer<T> valueSerializer) {
		return new ListSerializer<>(valueSerializer, true, false);
	}

	public static <T> ListSerializer<T> flagSet(IJeiConfigValueSerializer<T> valueSerializer) {
		return new ListSerializer<>(valueSerializer, false, true);
	}

	@Override
	public String serialize(List<T> values) {
		return values.stream()
			.map(valueSerializer::serialize)
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
			.map(valueSerializer::deserialize)
			.<T>mapMulti((r, c) -> {
				r.getResult().ifPresent(c);
				errors.addAll(r.getErrors());
			})
			.toList();

		return new DeserializeResult<>(results, errors);
	}

	@Override
	public String getValidValuesDescription() {
		return "A comma-separated list containing values of:\n%s".formatted(valueSerializer.getValidValuesDescription());
	}

	@Override
	public boolean isValid(List<T> value) {
		return value.stream()
			.allMatch(valueSerializer::isValid);
	}

	@Override
	public IJeiConfigValueSerializer<T> getListValueSerializer() {
		return valueSerializer;
	}

	@Override
	public boolean isOrderSensitive() {
		return orderSensitive;
	}

	@Override
	public boolean isFlagSet() {
		return flagSet;
	}

	@Override
	public Optional<Collection<List<T>>> getAllValidValues() {
		return Optional.empty();
	}
}
