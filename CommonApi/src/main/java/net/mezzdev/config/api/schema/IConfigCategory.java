package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Categories organize {@link IConfigValue}s into groups.
 * An {@link IConfigSchema} can contain one or more categories.
 * <p>
 * Add a category to your schema here: {@link IConfigSchemaBuilder#addCategory(String)}.
 * Get registered categories here: {@link IConfigSchema#getCategories()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigCategory {
	/**
	 * The name of the category.
	 *
	 * @since 0.1.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config category's name.
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();

	/**
	 * The config values in the category.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	Collection<? extends IConfigValue<?>> getConfigValues();
}
