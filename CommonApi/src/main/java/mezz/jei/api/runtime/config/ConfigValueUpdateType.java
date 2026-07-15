package mezz.jei.api.runtime.config;

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
	 * The value should be applied when the config screen's pending changes are applied.
	 *
	 * @since 19.39.0
	 */
	ON_APPLY,

	/**
	 * The value can be saved immediately, but JEI must restart before it fully takes effect.
	 *
	 * @since 19.39.0
	 */
	RESTART_JEI
}
