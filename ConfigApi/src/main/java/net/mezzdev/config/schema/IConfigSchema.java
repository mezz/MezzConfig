package net.mezzdev.config.schema;

import net.mezzdev.config.files.IConfigFile;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Config data prepared for display in a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigSchema extends IConfigFile {
	/**
	 * Get the categories and ordering to display in a config screen.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	List<? extends IConfigDisplayCategory> getDisplayCategories();
}
