package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.ConfigValueRange;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Serializer for bounded long config values.
 */
public final class LongSerializer implements IConfigValueSerializer<Long> {
	private final ConfigValueRange<Long> range;

	public LongSerializer(long min, long max) {
		if (min > max) {
			throw new IllegalArgumentException("min must be less than or equal to max.");
		}
		this.range = new ConfigValueRange<>(min, max);
	}

	@Override
	public Optional<ConfigValueRange<Long>> getRange() {
		return Optional.of(range);
	}

	@Override
	public String serialize(Long value) {
		return value.toString();
	}

	@Override
	public DeserializeResult<Long> deserialize(String string) {
		string = string.trim();
		try {
			long value = Long.parseLong(string);
			if (!isValid(value)) {
				String errorMessage = "Invalid long. Must be: " + getValidValuesDescription();
				return new DeserializeResult<>(null, errorMessage);
			}
			return new DeserializeResult<>(value);
		} catch (NumberFormatException e) {
			String errorMessage = "Unable to parse long: '%s' with error:\n%s".formatted(string, e.getMessage());
			return new DeserializeResult<>(null, errorMessage);
		}
	}

	@Override
	public String getValidValuesDescription() {
		long min = range.min();
		long max = range.max();
		if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) {
			return "Any long";
		}
		if (max == Long.MAX_VALUE) {
			return "Any long greater than or equal to %s".formatted(min);
		}

		return "A long in the range [%s, %s] (inclusive)".formatted(min, max);
	}

	@Override
	public boolean isValid(@Nullable Long value) {
		return value != null && value >= range.min() && value <= range.max();
	}

	@Override
	public Optional<Collection<Long>> getAllValidValues() {
		long min = range.min();
		long max = range.max();
		List<Long> values = new ArrayList<>();
		long value = min;
		while (true) {
			values.add(value);
			if (value == max) {
				if (values.size() < 20) {
					return Optional.of(values);
				}
				return Optional.empty();
			}
			if (values.size() >= 20 || value == Long.MAX_VALUE) {
				return Optional.empty();
			}
			value++;
		}
	}
}
