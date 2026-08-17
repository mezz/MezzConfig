package net.mezzdev.config.api;

import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Gives access to config schemas.
 * Useful for mods that let users change configs in-game.
 * Get the active manager from {@link Configs#getConfigManager()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigManager {
	/**
	 * @return all registered config schemas.
	 * @see IConfigSchema
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	Collection<? extends IConfigSchema> getSchemas();
}
