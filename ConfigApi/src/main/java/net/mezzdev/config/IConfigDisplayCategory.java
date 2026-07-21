package net.mezzdev.config;

/**
 * A user-facing category prepared for display in a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigDisplayCategory extends IConfigCategory {
	/**
	 * Get the optional role that a config screen can use to add caller-provided values.
	 *
	 * @since 19.39.0
	 */
	ConfigDisplayCategoryRole getRole();
}
