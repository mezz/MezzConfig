package net.mezzdev.config.schema;

import net.mezzdev.config.value.IConfigValueSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * A user-facing category prepared for display in a config screen.
 *
 * @since 19.39.0
 */
public interface IConfigDisplayCategory {
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
	 * Sources of config values for this category.
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	Collection<? extends IConfigValueSource> getConfigValueSources();
}
