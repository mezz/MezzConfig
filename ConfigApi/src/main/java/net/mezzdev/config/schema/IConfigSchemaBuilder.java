package net.mezzdev.config.schema;

/**
 * Builds one config schema.
 *
 * @since 19.39.0
 */
public interface IConfigSchemaBuilder {
	/**
	 * Add a storage category to this config file.
	 *
	 * @param name stable storage name for the category
	 *
	 * @since 19.39.0
	 */
	IConfigCategoryBuilder addCategory(String name);

	/**
	 * Add a user-facing display category to this config screen.
	 * <p>
	 * The category translation key is the schema translation key followed by {@code .} and {@code name}.
	 *
	 * @param name stable display name for the category
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addDisplayCategory(String name);

	/**
	 * Build the config schema.
	 *
	 * @since 19.39.0
	 */
	IConfigSchema build();
}
