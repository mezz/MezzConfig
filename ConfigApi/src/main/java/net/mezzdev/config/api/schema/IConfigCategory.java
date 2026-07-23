package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.files.IConfigFile;
import net.mezzdev.config.api.value.IConfigValue;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Categories organize {@link IConfigValue}s into groups.
 * An {@link IConfigFile} can contain one or more categories.
 *
 * @since 19.39.0
 */
public interface IConfigCategory {
	/**
	 * The name of the category.
	 *
	 * @since 19.39.0
	 */
	String getName();

	/**
	 * Get the translated name component of this config category.
	 *
	 * @since 19.39.0
	 */
	Component getLocalizedName();

	/**
	 * Get the translated description component of this config category.
	 *
	 * @since 19.39.0
	 */
	Component getLocalizedDescription();

	/**
	 * The config values in the category.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	Collection<? extends IConfigValue<?>> getConfigValues();
}
