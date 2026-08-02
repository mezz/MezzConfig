package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/**
 * Builds one config value.
 * <p>
 * An instance is returned when your plugin creates a config value here:
 * {@link IConfigCategoryBuilder#addValue(String, Object, IConfigValueSerializer)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValueBuilder<T> {
	/**
	 * Add an old storage name for this value.
	 * <p>
	 * Use this when this value has been renamed but its serialized format has not changed. Values found with the old
	 * name are loaded into this value.
	 *
	 * @param legacyName old stable storage name for this value
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> addLegacyName(String legacyName);

	/**
	 * Add a migration from old serialized text for this value.
	 * <p>
	 * Use this when this value's serialized format or type has changed. The migration is used for this value's
	 * current storage name and for any legacy category or value names declared on this builder.
	 *
	 * @param migration converts the old serialized text into the current value type
	 *
	 * @since 0.1.0
	 */
	IConfigValueBuilder<T> addLegacyValueMigration(Function<String, T> migration);

	/**
	 * Build and add the config value to its category.
	 * A value builder may only be built once.
	 *
	 * @since 0.1.0
	 */
	IConfigValue<T> build();
}
