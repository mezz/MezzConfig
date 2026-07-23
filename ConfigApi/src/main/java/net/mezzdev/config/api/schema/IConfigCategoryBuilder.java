package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.ConfigValueUpdateType;
import net.mezzdev.config.api.value.IConfigListValueSerializer;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;

import java.util.List;

/**
 * Builds config values for one config category.
 *
 * @since 19.39.0
 */
public interface IConfigCategoryBuilder {
	/**
	 * Add a config value with a custom serializer.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param serializer serializer for reading, writing, validation, and display metadata
	 * @param updateType kind of update needed for changes to take effect
	 *
	 * @since 19.39.0
	 */
	<T> IConfigValue<T> addValue(String name, T defaultValue, IConfigValueSerializer<T> serializer, ConfigValueUpdateType updateType);

	/**
	 * Add a boolean config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param updateType kind of update needed for changes to take effect
	 *
	 * @since 19.39.0
	 */
	IConfigValue<Boolean> addBoolean(String name, boolean defaultValue, ConfigValueUpdateType updateType);

	/**
	 * Add an integer config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param minValue smallest valid value
	 * @param maxValue largest valid value
	 * @param updateType kind of update needed for changes to take effect
	 *
	 * @since 19.39.0
	 */
	IConfigValue<Integer> addInteger(String name, int defaultValue, int minValue, int maxValue, ConfigValueUpdateType updateType);

	/**
	 * Add an enum config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param updateType kind of update needed for changes to take effect
	 *
	 * @since 19.39.0
	 */
	<T extends Enum<T>> IConfigValue<T> addEnum(String name, T defaultValue, ConfigValueUpdateType updateType);

	/**
	 * Add a list config value.
	 *
	 * @param name stable storage name for the value
	 * @param defaultValue default value
	 * @param listSerializer serializer for the list and its element values
	 * @param updateType kind of update needed for changes to take effect
	 *
	 * @since 19.39.0
	 */
	<T> IConfigValue<List<T>> addList(String name, List<T> defaultValue, IConfigListValueSerializer<T> listSerializer, ConfigValueUpdateType updateType);
}
