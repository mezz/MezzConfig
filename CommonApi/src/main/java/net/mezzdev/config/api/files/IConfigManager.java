package net.mezzdev.config.api.files;

import net.mezzdev.config.api.plugin.IConfigRegistration;
import net.mezzdev.config.api.plugin.IServerConfigRegistration;
import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Gives access to config schemas.
 * Useful for mods that let users change configs in-game.
 * <p>
 * An instance is available during plugin registration from {@link IConfigRegistration#getConfigManager()} or
 * {@link IServerConfigRegistration#getConfigManager()}.
 * Config editors can get the active manager here: {@link ConfigManagers#getConfigManager()}.
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
