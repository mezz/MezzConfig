package net.mezzdev.config.keybindings;

import net.minecraft.network.chat.Component;

/**
 * Display information for a key binding conflict.
 *
 * @param name the localized name of the conflicting key mapping
 * @param binding the localized name of the conflicting key binding
 * @param modName the localized name of the mod that owns the conflicting key mapping
 * @param category the localized name of the key mapping category
 */
public record ConfigKeyMappingConflict(
	Component name,
	Component binding,
	Component modName,
	Component category
) {

}
