package net.mezzdev.config.api.schema;

import org.jetbrains.annotations.ApiStatus;

/**
 * Builds an editor-only category.
 * <p>
 * An instance is returned when your plugin creates an editor-only category here:
 * {@link IConfigSchemaBuilder#addEditorCategory(String)}.
 * Storage category builders from {@link IConfigSchemaBuilder#addCategory(String)} can also be used as editor
 * categories.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigEditorCategoryBuilder {

}
