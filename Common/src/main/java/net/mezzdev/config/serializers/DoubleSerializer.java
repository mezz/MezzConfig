package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.ConfigValueRange;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Serializer for bounded double config values.
 */
public final class DoubleSerializer implements IConfigValueSerializer<Double> {
	private final ConfigValueRange<Double> range;

	public DoubleSerializer(double min, double max) {
		if (!Double.isFinite(min) || !Double.isFinite(max)) {
			throw new IllegalArgumentException("min and max must be finite.");
		}
		if (min > max) {
			throw new IllegalArgumentException("min must be less than or equal to max.");
		}
		this.range = new ConfigValueRange<>(min, max);
	}

	@Override
	public Optional<ConfigValueRange<Double>> getRange() {
		return Optional.of(range);
	}

	@Override
	public String serialize(Double value) {
		return value.toString();
	}

	@Override
	public DeserializeResult<Double> deserialize(String string) {
		string = string.trim();
		try {
			double value = Double.parseDouble(string);
			if (!isValid(value)) {
				String errorMessage = "Invalid double. Must be: " + getValidValuesDescription();
				return new DeserializeResult<>(null, errorMessage);
			}
			return new DeserializeResult<>(value);
		} catch (NumberFormatException e) {
			String errorMessage = "Unable to parse double: '%s' with error:\n%s".formatted(string, e.getMessage());
			return new DeserializeResult<>(null, errorMessage);
		}
	}

	@Override
	public String getValidValuesDescription() {
		double min = range.min();
		double max = range.max();
		if (min == -Double.MAX_VALUE && max == Double.MAX_VALUE) {
			return "Any finite double";
		}
		if (max == Double.MAX_VALUE) {
			return "Any finite double greater than or equal to %s".formatted(min);
		}

		return "A finite double in the range [%s, %s] (inclusive)".formatted(min, max);
	}

	@Override
	public boolean isValid(@Nullable Double value) {
		return value != null &&
			Double.isFinite(value) &&
			value >= range.min() &&
			value <= range.max();
	}

	@Override
	public Optional<Collection<Double>> getAllValidValues() {
		return Optional.empty();
	}
}
