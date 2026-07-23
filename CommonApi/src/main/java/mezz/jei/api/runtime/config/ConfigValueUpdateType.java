package mezz.jei.api.runtime.config;

/**
 * Describes how a config value should be applied after it is changed.
 *
 * @since 19.39.0
 * @deprecated use {@code net.mezzdev.config.api.value.ConfigValueUpdateType}
 */
@Deprecated(since = "19.39.0", forRemoval = false)
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
	 * The value can be saved immediately, but JEI must restart before it fully takes effect.
	 *
	 * @since 19.39.0
	 */
	RESTART_JEI
}
