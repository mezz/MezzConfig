package net.mezzdev.config.value;

/**
 * Describes how a config value should be applied after it is changed.
 *
 * @since 19.39.0
 */
public enum ConfigValueUpdateType {
	/**
	 * The value can be applied as soon as it changes.
	 *
	 * @since 19.39.0
	 */
	IMMEDIATE,

	/**
	 * The value is saved and applied with the config screen's pending changes.
	 * Use this for changes that should take effect together instead of after every intermediate edit.
	 *
	 * @since 19.39.0
	 */
	ON_APPLY,

	/**
	 * The value can be saved immediately, but a larger reload or restart is needed before it fully takes effect.
	 *
	 * @since 19.39.0
	 */
	RESTART
}
