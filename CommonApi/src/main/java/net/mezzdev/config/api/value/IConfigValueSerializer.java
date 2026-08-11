package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

/**
 * Serialization and validation helper for config values.
 * <p>
 * Pass your serializer to
 * {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)}
 * or as the element serializer in
 * {@link IConfigCategoryBuilder#addList(String, List, IConfigValueSerializer)}.
 * <p>
 * For list config values that should expose their element serializer, implement {@link IConfigListValueSerializer}.
 * For values composed of independently editable key and value components, implement {@link IConfigKeyValueSerializer}.
 * <p>
 * Config values must be effectively immutable while held by MezzConfig, and their {@link Object#equals(Object)} result
 * must remain stable. Custom serializers must return immutable values from {@link #deserialize(String)}, and callers
 * must not pass mutable values to config value builders or updates.
 *
 * @param <T> effectively immutable value type with stable equality
 *
 * @since 0.1.0
 */
public interface IConfigValueSerializer<T> {
	/**
	 * Serialize the config value to a string.
	 *
	 * @since 0.1.0
	 */
	String serialize(T value);

	/**
	 * Deserialize the config value from a string.
	 * The returned result must obey the state invariants documented by {@link IDeserializeResult}.
	 *
	 * @since 0.1.0
	 */
	IDeserializeResult<T> deserialize(String string);

	/**
	 * Check if a given value is valid for this config value.
	 *
	 * @since 0.1.0
	 */
	boolean isValid(T value);

	/**
	 * If this config value should be edited as a bounded range,
	 * this returns its inclusive lower and upper bounds.
	 * <p>
	 * If this config value does not have a range, this will return
	 * {@link Optional#empty()}.
	 *
	 * @since 0.1.0
	 */
	default Optional<ConfigValueRange<T>> getRange() {
		return Optional.empty();
	}

	/**
	 * If this config value only has a limited number of valid values,
	 * this returns them all in a stable presentation order.
	 * <p>
	 * The returned list must be unmodifiable and duplicate-free. Its order must remain stable while the set of valid
	 * values is unchanged. If there are many or unlimited valid values, this will return {@link Optional#empty()}.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	default Optional<List<T>> getAllValidValues() {
		return Optional.empty();
	}

	/**
	 * Get the description of what values are valid for this config value.
	 *
	 * @since 0.1.0
	 */
	String getValidValuesDescription();
}
