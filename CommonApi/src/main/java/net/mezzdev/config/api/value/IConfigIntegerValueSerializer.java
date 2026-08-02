package net.mezzdev.config.api.value;

/**
 * Serialization, validation, and editor metadata for integer config values.
 *
 * @since 19.39.0
 */
public interface IConfigIntegerValueSerializer extends IConfigValueEditorSerializer<Integer> {
	/**
	 * The smallest valid value.
	 *
	 * @since 19.39.0
	 */
	int getMin();

	/**
	 * The largest valid value.
	 *
	 * @since 19.39.0
	 */
	int getMax();
}
