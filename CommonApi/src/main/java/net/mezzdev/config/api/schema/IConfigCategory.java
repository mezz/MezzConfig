package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Storage categories organize {@link IConfigValue}s into config file groups.
 * An {@link IConfigSchema} can contain one or more categories.
 * <p>
 * Add a category to your schema here: {@link IConfigSchemaBuilder#addCategory(String)}.
 * Get registered categories here: {@link IConfigSchema#getCategories()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigCategory extends IConfigEditorCategory {
	/**
	 * The config values in the category.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	Collection<? extends IConfigValue<?>> getConfigValues();
}
