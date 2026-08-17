package net.mezzdev.config.api.schema;

/**
 * Identifies who owns a config schema's effective values.
 *
 * @since 0.3.0
 */
public enum ConfigOwnership {
	/**
	 * Values owned by the local client.
	 *
	 * @since 0.3.0
	 */
	CLIENT,

	/**
	 * Values owned by the local or connected server.
	 *
	 * @since 0.3.0
	 */
	SERVER
}
