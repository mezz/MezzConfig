package mezz.jei.api.runtime.config;

import java.util.List;

/**
 * Serialization and validation helper for JEI config values.
 *
 * @since 12.1.1
 */
public interface IJeiConfigListValueSerializer<T> extends IJeiConfigValueSerializer<List<T>> {
	/**
	 * Get the serializer for each value in the list.
	 *
	 * @since 12.1.1
	 */
	IJeiConfigValueSerializer<T> getListValueSerializer();

	/**
	 * Return true when changing the order of this list changes behavior.
	 *
	 * @since 19.21.0
	 */
	default boolean isOrderSensitive() {
		return false;
	}

	/**
	 * Return true when this list represents enabled values chosen from all valid values.
	 *
	 * @since 19.21.0
	 */
	default boolean isFlagSet() {
		return false;
	}
}
