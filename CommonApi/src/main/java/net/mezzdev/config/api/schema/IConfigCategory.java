package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A named group of values stored together in a config file.
 * <p>
 * Get the categories for a built schema from {@link IConfigSchema#getCategories()}. Config screens can use them for their
 * default visual grouping, or use {@link IConfigSchema#getEditorCategories()} when the schema defines a separate layout.
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
