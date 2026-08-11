package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serializer for enum config values.
 */
public class EnumSerializer<T extends Enum<T>> implements IConfigValueSerializer<T> {
	private final Class<T> enumClass;
	private final Collection<T> validValues;

	public EnumSerializer(Class<T> enumClass) {
		this.enumClass = Objects.requireNonNull(enumClass, "enumClass");
		this.validValues = List.of(this.enumClass.getEnumConstants());
	}

	public EnumSerializer(Class<T> enumClass, Collection<T> validValues) {
		this.enumClass = Objects.requireNonNull(enumClass, "enumClass");
		Objects.requireNonNull(validValues, "validValues");
		if (validValues.isEmpty()) {
			throw new IllegalArgumentException("validValues must not be empty.");
		}

		Set<T> checkedValidValues = new LinkedHashSet<>();
		for (T validValue : validValues) {
			Objects.requireNonNull(validValue, "validValue");
			if (validValue.getDeclaringClass() != this.enumClass) {
				throw new IllegalArgumentException("Valid enum value does not belong to %s: %s".formatted(
					this.enumClass.getCanonicalName(),
					validValue
				));
			}
			if (!checkedValidValues.add(validValue)) {
				throw new IllegalArgumentException("Duplicate valid enum value: " + validValue);
			}
		}
		this.validValues = List.copyOf(checkedValidValues);
	}

	@Override
	public String serialize(T value) {
		return value.name();
	}

	@Override
	public DeserializeResult<T> deserialize(String string) {
		String enumName = normalizeSerializedEnumName(string);
		T enumValue = getEnumValue(enumClass, enumName);
		if (enumValue != null && isValid(enumValue)) {
			return DeserializeResult.success(enumValue);
		}

		return DeserializeResult.failure("Invalid enum name. Must be: " + getValidValuesDescription());
	}

	@Override
	public String getValidValuesDescription() {
		String names = validValues.stream()
			.map(Enum::name)
			.collect(Collectors.joining(", "));

		return "[%s]".formatted(names);
	}

	@Override
	public boolean isValid(T value) {
		return validValues.contains(value);
	}

	@Override
	public Optional<Collection<T>> getAllValidValues() {
		return Optional.of(validValues);
	}

	@Nullable
	private static <T extends Enum<T>> T getEnumValue(Class<T> enumClass, String enumName) {
		try {
			return Enum.valueOf(enumClass, enumName);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String normalizeSerializedEnumName(String string) {
		string = string.trim();
		if (string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1);
		}
		return string;
	}
}
