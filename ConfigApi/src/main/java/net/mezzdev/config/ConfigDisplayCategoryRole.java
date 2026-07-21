package net.mezzdev.config;

/**
 * Optional well-known roles that config GUIs can use to attach extra display-only values.
 *
 * @since 19.39.0
 */
public enum ConfigDisplayCategoryRole {
	/**
	 * A normal display category.
	 *
	 * @since 19.39.0
	 */
	DEFAULT,

	/**
	 * A category that can be extended with key mapping values supplied by the config screen caller.
	 *
	 * @since 19.39.0
	 */
	KEY_MAPPINGS
}
