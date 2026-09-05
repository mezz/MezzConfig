package net.mezzdev.config.api.value.serializer;

/**
 * Describes whether the order of entries in a list config value is meaningful.
 *
 * @since 0.1.0
 */
public enum ConfigListOrdering {
	/**
	 * Changing the order changes the meaning of the config value.
	 *
	 * @since 0.1.0
	 */
	ORDERED,

	/**
	 * Changing the order does not change the meaning of the config value.
	 *
	 * @since 0.1.0
	 */
	UNORDERED
}
