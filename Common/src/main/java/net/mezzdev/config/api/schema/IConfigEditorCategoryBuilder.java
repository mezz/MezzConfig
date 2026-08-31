package net.mezzdev.config.api.schema;

import org.jetbrains.annotations.ApiStatus;

/**
 * Identifies a config-screen category while a schema is being declared.
 * <p>
 * Get an instance from {@link IConfigSchemaBuilder#addEditorCategory(String)}, then pass it to config value builders to
 * place values in that category. Storage category builders can be used in the same way.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigEditorCategoryBuilder {

}
