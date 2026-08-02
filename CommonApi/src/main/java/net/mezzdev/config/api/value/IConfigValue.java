package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Represents a config value.
 * Config values can be read or updated by mods.
 *
 * These config values are automatically synced with the config file.
 * {@link #getValue()} will automatically update based on changes to the file,
 * and using {@link #set} will automatically update the file.
 * <p>
 * Add a value to your category with the add methods on {@link IConfigCategoryBuilder}.
 * Get registered values here: {@link IConfigCategory#getConfigValues()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValue<T> {
	/**
	 * Get the name of this config value.
	 *
	 * @since 0.1.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config value's name.
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();

	/**
	 * Get the current value.
	 * This will automatically update and load from the config file if there are changes.
	 *
	 * @since 0.1.0
	 */
	T getValue();

	/**
	 * Get the default value.
	 *
	 * @since 0.1.0
	 */
	T getDefaultValue();

	/**
	 * Set the config value to the given value.
	 * This will automatically mark the config file as dirty so that it will save the new values.
	 *
	 * @since 0.1.0
	 */
	boolean set(T value);

	/**
	 * Add a listener that is called with the new value when this config value changes.
	 *
	 * @since 0.1.0
	 */
	void addListener(Consumer<T> listener);

	/**
	 * Add a listener that is called with the old and new values when this config value changes.
	 *
	 * @param listener callback accepting the old value and new value
	 *
	 * @since 0.1.0
	 */
	default void addListener(IConfigValueChangeListener<T> listener) {
		addListener(newValue -> listener.onChange(newValue, newValue));
	}

	/**
	 * Get the helper for serializing values to and from Strings, and validating values.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
