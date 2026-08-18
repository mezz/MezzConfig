package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

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
	 * Get the config values in builder insertion order.
	 *
	 * @return an immutable list snapshot of the category's config values
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigValue<?>> getConfigValues();
}
