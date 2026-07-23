package net.mezzdev.config.value;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Represents a config value.
 * Config values can be read or updated by mods that display in-game config files to players.
 *
 * These config values are automatically synced with the config file.
 * {@link #getValue()} will automatically update based on changes to the file,
 * and using {@link #set} will automatically update the file.
 *
 * @since 19.39.0
 */
public interface IConfigValue<T> {
	/**
	 * Get the name of this config value.
	 *
	 * @since 19.39.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config value's name.
	 *
	 * @since 19.39.0
	 */
	String getLocalizationKey();

	/**
	 * Get the translated name component of this config value.
	 *
	 * @since 19.39.0
	 */
	Component getLocalizedName();

	/**
	 * Get the translated description component of this config value.
	 *
	 * @since 19.39.0
	 */
	Component getLocalizedDescription();

	/**
	 * Get the current value.
	 * This will automatically update and load from the config file if there are changes.
	 *
	 * @since 19.39.0
	 */
	T getValue();

	/**
	 * Get the default value.
	 *
	 * @since 19.39.0
	 */
	T getDefaultValue();

	/**
	 * Set the config value to the given value.
	 * This will automatically mark the config file as dirty so that it will save the new values.
	 *
	 * @since 19.39.0
	 */
	boolean set(T value);

	/**
	 * Add a listener that is called when this config value changes.
	 *
	 * @since 19.39.0
	 */
	void addListener(Consumer<T> listener);

	/**
	 * Get the kind of update needed for this config value to take effect.
	 *
	 * @since 19.39.0
	 */
	ConfigValueUpdateType getUpdateType();

	/**
	 * Get the helper for serializing values to and from Strings, and validating values.
	 *
	 * @since 19.39.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
