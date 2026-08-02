package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.ConfigValueRange;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Serializer for bounded integer config values.
 */
public final class IntegerSerializer implements IConfigValueSerializer<Integer> {
	private final ConfigValueRange<Integer> range;

	public IntegerSerializer(int min, int max) {
		if (min > max) {
			throw new IllegalArgumentException("min must be less than or equal to max.");
		}
		this.range = new ConfigValueRange<>(min, max);
	}

	@Override
	public Optional<ConfigValueRange<Integer>> getRange() {
		return Optional.of(range);
	}

	@Override
	public String serialize(Integer value) {
		return value.toString();
	}

	@Override
	public DeserializeResult<Integer> deserialize(String string) {
		string = string.trim();
		try {
			int value = Integer.parseInt(string);
			if (!isValid(value)) {
				String errorMessage = "Invalid integer. Must be: " + getValidValuesDescription();
				return new DeserializeResult<>(null, errorMessage);
			}
			return new DeserializeResult<>(value);
		} catch (NumberFormatException e) {
			String errorMessage = "Unable to parse int: '%s' with error:\n%s".formatted(string, e.getMessage());
			return new DeserializeResult<>(null, errorMessage);
		}
	}

	@Override
	public String getValidValuesDescription() {
		int min = range.min();
		int max = range.max();
		if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) {
			return "Any integer";
		}
		if (max == Integer.MAX_VALUE) {
			return "Any integer greater than or equal to %s".formatted(min);
		}

		return "An integer in the range [%s, %s] (inclusive)".formatted(min, max);
	}

	@Override
	public boolean isValid(@Nullable Integer value) {
		return value != null && value >= range.min() && value <= range.max();
	}

	@Override
	public Optional<Collection<Integer>> getAllValidValues() {
		int min = range.min();
		int max = range.max();
		int count = max - min + 1;
		if (count > 0 && count < 20) {
			List<Integer> values = IntStream.rangeClosed(min, max)
				.boxed()
				.toList();
			return Optional.of(values);
		}
		return Optional.empty();
	}

}
