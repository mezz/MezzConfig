package net.mezzdev.config;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Gives access to JEI's config files.
 * Useful for mods that let users change configs in-game.
 *
 * Get an instance from JEI's runtime.
 *
 * @since 12.1.0
 */
public interface IJeiConfigManager {
	/**
	 * @return all of JEI's config files.
	 * @see IJeiConfigFile
	 *
	 * @since 12.1.0
	 */
	@Unmodifiable
	Collection<IJeiConfigFile> getConfigFiles();
}
