package net.mezzdev.config.api.schema;

/**
 * Describes who owns a config schema and how its active values are selected.
 *
 * @since 0.2.0
 */
public enum ConfigSchemaType {
	/**
	 * Client-owned preferences that remain active across worlds and server connections.
	 *
	 * @since 0.2.0
	 */
	CLIENT,

	/**
	 * Client-owned preferences with a separate file for each singleplayer world or multiplayer server.
	 *
	 * @since 0.2.0
	 */
	CLIENT_WORLD,

	/**
	 * Server-owned settings stored with the world and synchronized to connected clients.
	 *
	 * @since 0.2.0
	 */
	SERVER
}
