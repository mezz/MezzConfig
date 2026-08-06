package net.mezzdev.config.api.schema;

import org.jetbrains.annotations.ApiStatus;

/**
 * A category where config editors can show config values.
 * <p>
 * Create editor-only categories here: {@link IConfigSchemaBuilder#addEditorCategory(String)}.
 * Storage categories from {@link IConfigSchemaBuilder#addCategory(String)} are also editor categories.
 * Get registered editor categories here: {@link IConfigSchema#getEditorCategories()}.
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
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();
}
