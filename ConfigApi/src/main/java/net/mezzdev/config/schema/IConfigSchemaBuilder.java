package net.mezzdev.config.schema;

import net.mezzdev.config.value.IConfigValueSerializer;

import java.util.function.Consumer;

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
	 * Add a migration for a value that used to be stored under a different category and name.
	 *
	 * @param categoryName old stable storage name for the category
	 * @param valueName old stable storage name for the value
	 * @param serializer serializer for the old value
	 * @param migration receives the old value and updates current config values
	 *
	 * @since 19.39.0
	 */
	<T> void addLegacyValueMigration(
		String categoryName,
		String valueName,
		IConfigValueSerializer<T> serializer,
		Consumer<T> migration
	);

	/**
	 * Build and register the config schema.
	 * A builder may only be built once.
	 *
	 * @since 19.39.0
	 */
	IConfigEditableSchema build();
}
