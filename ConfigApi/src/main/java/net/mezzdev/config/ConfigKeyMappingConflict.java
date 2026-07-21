package net.mezzdev.config;

import net.minecraft.network.chat.Component;

/**
 * Display information for a key binding conflict.
 *
 * @since 19.39.0
 */
public record ConfigKeyMappingConflict(
	/**
	 * The localized name of the conflicting key mapping.
	 *
	 * @since 19.39.0
	 */
	Component name,
	/**
	 * The localized name of the conflicting key binding.
	 *
	 * @since 19.39.0
	 */
	Component binding,
	/**
	 * The localized name of the mod that owns the conflicting key mapping.
	 *
	 * @since 19.39.0
	 */
	Component modName,
	/**
	 * The localized name of the key mapping category.
	 *
	 * @since 19.39.0
	 */
	Component category
) {

}
