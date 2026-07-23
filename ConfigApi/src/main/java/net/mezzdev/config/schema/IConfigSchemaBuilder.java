package net.mezzdev.config.schema;

import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.value.IConfigValueSerializer;

import java.util.function.Function;

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
	 * @param legacyCategoryName old stable storage name for the category
	 * @param legacyValueName    old stable storage name for the value
	 * @param legacySerializer   serializer for the old value
	 * @param migration          converts the old value into the current value
	 * @param newConfigValue     current config value to update
	 * @since 19.39.0
	 */
	<T, R> void addLegacyValueMigration(
		String legacyCategoryName,
		String legacyValueName,
		IConfigValueSerializer<T> legacySerializer,
		Function<T, R> migration,
		IConfigValue<R> newConfigValue
	);

	/**
	 * Build and register the config schema.
	 * A builder may only be built once.
	 *
	 * @since 19.39.0
	 */
	IConfigEditableSchema build();
}
