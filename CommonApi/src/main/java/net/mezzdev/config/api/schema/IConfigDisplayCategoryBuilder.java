package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;

import java.util.Collection;

/**
 * Builds one user-facing display category for a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigDisplayCategoryBuilder {
	/**
	 * Add one config value to this display category.
	 *
	 * @param value config value to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addValue(IConfigValue<?> value);

	/**
	 * Add config values to this display category.
	 *
	 * @param values config values to display
	 *
	 * @since 19.39.0
	 */
	IConfigDisplayCategoryBuilder addValues(Collection<? extends IConfigValue<?>> values);
}
