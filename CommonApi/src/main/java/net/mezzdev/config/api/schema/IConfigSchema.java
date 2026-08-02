package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents one registered config schema and its backing file.
 *
 * Config schemas contain one or more {@link IConfigCategory},
 * and each category has one or more {@link IConfigValue}.
 * <p>
 * Create and register your schema here: {@link IConfigSchemaBuilder#build()}.
 * Get registered schemas here: {@link IConfigManager#getConfigFiles()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchema {
	/**
	 * Get the path of this config schema.
	 * Used to identify the backing file.
	 *
	 * Note that config values will read from this file automatically,
	 * and updating config values will save the file automatically,
	 * so you should not read or write this file yourself.
	 *
	 * @since 0.1.0
	 */
	Path getPath();

	/**
	 * Get all the categories in this schema.
	 * Each category contains values that can be read or edited.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigCategory> getCategories();

	/**
	 * Clear listeners from values in this schema.
	 * Useful when tearing down per-runtime listeners.
	 *
	 * @since 0.1.0
	 */
	void clearListeners();
}
