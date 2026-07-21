package net.mezzdev.config;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Gives access to config files.
 * Useful for mods that let users change configs in-game.
 *
 * @since 19.39.0
 */
public interface IConfigManager {
	/**
	 * @return all registered config files.
	 * @see IConfigFile
	 *
	 * @since 19.39.0
	 */
	@Unmodifiable
	Collection<? extends IConfigFile> getConfigFiles();
}
