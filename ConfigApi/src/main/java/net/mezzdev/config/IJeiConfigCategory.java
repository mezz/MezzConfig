package net.mezzdev.config;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Categories organize {@link IJeiConfigValue}s into groups.
 * An {@link IJeiConfigFile} can contain one or more categories.
 *
 * @since 12.1.0
 */
public interface IJeiConfigCategory {
	/**
	 * The name of the category.
	 *
	 * @since 12.1.0
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
	Component getDescription();

	/**
	 * The config values in the category.
	 *
	 * @since 12.1.0
	 */
	@Unmodifiable
	Collection<? extends IJeiConfigValue<?>> getConfigValues();
}
