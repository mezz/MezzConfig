package net.mezzdev.config.files;

import net.mezzdev.config.schema.IConfigCategory;
import net.mezzdev.config.value.IConfigValue;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents one config file.
 *
 * Config files contain one or more {@link IConfigCategory},
 * and each category has one or more {@link IConfigValue}.
 *
 * @since 19.39.0
 */
public interface IConfigFile {
	/**
	 * Get the path of this config file.
	 * Used for differentiating between config files.
	 *
	 * Note that config values will read from this file automatically,
	 * and updating config values will save the file automatically,
	 * so you should not read or write this file yourself.
	 *
	 * @since 19.39.0
	 */
	Path getPath();

	/**
	 * Get all the categories in this file.
	 * Each category contains values that can be read or edited.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	List<? extends IConfigCategory> getCategories();
}
