package net.mezzdev.config.api.schema;

/**
 * Identifies the context that selects a config schema's backing file.
 *
 * @since 0.3.0
 */
public enum ConfigScope {
	/**
	 * One config for the local game installation.
	 *
	 * @since 0.3.0
	 */
	INSTALLATION,

	/**
	 * Values belonging to the active world. On a client, a remote server selects that server's world context.
	 *
	 * @since 0.3.0
	 */
	WORLD
}
