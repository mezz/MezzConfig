package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.plugin.IConfigRegistration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Builds one config schema.
 * <p>
 * Create a builder for your schema here: {@link IConfigRegistration#createSchemaBuilder(String, String)}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchemaBuilder {
	/**
	 * Add a storage category to this config schema.
	 *
	 * @param name stable storage name for the category
	 *
	 * @since 0.1.0
	 */
	IConfigCategoryBuilder addCategory(String name);

	/**
	 * Build and register the config schema.
	 * A builder may only be built once.
	 *
	 * @since 0.1.0
	 */
	IConfigSchema build();
}
