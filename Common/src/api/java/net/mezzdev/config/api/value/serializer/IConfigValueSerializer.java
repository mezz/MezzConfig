package net.mezzdev.config.api.value.serializer;

import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

/**
 * Teaches MezzConfig how to store, validate, and describe a mod-specific value type.
 * <p>
 * Prefer built-in value methods when possible. For a custom type, implement this interface and pass it to
 * {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)}. Implement
 * {@link IConfigListValueSerializer} when editors should work with list elements individually, or
 * {@link IConfigKeyValueSerializer} when editors should work with two components of an entry.
 * <p>
 * Accepted values must be effectively immutable with stable equality. Serialization must be deterministic, and every
 * accepted value must deserialize back to an equal value without diagnostics. Deserialization must report invalid input
 * through {@link IDeserializeResult} rather than throw; every returned value must pass {@link #isValid(Object)}. Shared
 * serializer instances must be thread-safe.
 *
 * @param <T> effectively immutable value type with stable equality
 *
 * @since 0.1.0
 */
public interface IConfigValueSerializer<T> {
	/**
	 * Convert a valid value to its stable text representation.
	 *
	 * @since 0.1.0
	 */
	String serialize(T value);

	/**
	 * Parse stored text, returning diagnostics instead of throwing when the input is invalid.
	 *
	 * @since 0.1.0
	 */
	IDeserializeResult<T> deserialize(String string);

	/**
	 * Return whether this value can be safely stored.
	 *
	 * @since 0.1.0
	 */
	boolean isValid(T value);

	/**
	 * Describe an inclusive range that config editors can present with a bounded control.
	 *
	 * @since 0.1.0
	 */
	default Optional<ConfigValueRange<T>> getRange() {
		return Optional.empty();
	}

	/**
	 * List every valid choice when config editors should present a fixed selection.
	 * <p>
	 * The returned list must be unmodifiable and duplicate-free. Its order must remain stable while the set of valid
	 * values is unchanged. Return {@link Optional#empty()} for open-ended value types.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	default Optional<List<T>> getAllValidValues() {
		return Optional.empty();
	}

	/**
	 * Describe valid input for config editors and error messages.
	 *
	 * @since 0.1.0
	 */
	String getValidValuesDescription();
}
