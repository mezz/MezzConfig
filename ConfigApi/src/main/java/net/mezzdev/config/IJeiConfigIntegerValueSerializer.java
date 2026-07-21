package net.mezzdev.config;

/**
 * Serialization, validation, and editor metadata for integer config values.
 *
 * @since 19.39.0
 */
public interface IJeiConfigIntegerValueSerializer extends IJeiConfigValueSerializer<Integer> {
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

	@Override
	default ConfigValueEditorType<Integer> getEditorType() {
		return ConfigValueEditorTypes.INTEGER;
	}
}
