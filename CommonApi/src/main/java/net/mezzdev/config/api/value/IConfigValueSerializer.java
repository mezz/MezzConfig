package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Optional;

/**
 * Serialization and validation helper for config values.
 * <p>
 * Pass your serializer to
 * {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)}
 * or as the element serializer in
 * {@link IConfigCategoryBuilder#addList(String, java.util.List, IConfigValueSerializer)}.
 * <p>
 * For list config values that should expose their element serializer, implement {@link IConfigListValueSerializer}.
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
	 * this returns them all.
	 * <p>
	 * If there are many or unlimited valid values, this will return
	 * {@link Optional#empty()}
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	Optional<Collection<T>> getAllValidValues();

	/**
	 * Get the description of what values are valid for this config value.
	 *
	 * @since 0.1.0
	 */
	String getValidValuesDescription();
}
