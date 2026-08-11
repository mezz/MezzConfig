package net.mezzdev.config.api.value;

/**
 * State of a config value deserialization result.
 *
 * @since 0.1.0
 */
public enum DeserializeResultState {
	/**
	 * A value was fully deserialized and there are no diagnostics.
	 *
	 * @since 0.1.0
	 */
	SUCCESS,

	/**
	 * A usable value was deserialized, but one or more diagnostics describe input that could not be recovered.
	 *
	 * @since 0.1.0
	 */
	PARTIAL_SUCCESS,

	/**
	 * No value could be deserialized, and one or more diagnostics describe the failure.
	 *
	 * @since 0.1.0
	 */
	FAILURE
}
