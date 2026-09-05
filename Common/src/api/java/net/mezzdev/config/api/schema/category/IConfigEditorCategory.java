package net.mezzdev.config.api.schema.category;

import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;

/**
 * Describes a visual group that config screens can use to present related values.
 * <p>
 * Get editor categories from {@link IConfigSchema#getEditorCategories()}. Every storage category is also an editor
 * category; schemas can add editor-only categories when the most useful screen layout differs from the config file.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigEditorCategory {
	/**
	 * The name of the category.
	 *
	 * @since 0.1.0
	 */
	String getName();

	/**
	 * Get the translation key used for this category's name.
	 * The category description uses this key with {@code .description} appended.
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();
}
