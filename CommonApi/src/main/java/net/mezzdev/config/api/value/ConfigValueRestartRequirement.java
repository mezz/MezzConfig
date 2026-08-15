package net.mezzdev.config.api.value;

/**
 * Defines when a saved config value becomes effective.
 *
 * @since 0.1.0
 */
public enum ConfigValueRestartRequirement {
	/**
	 * Changes can take effect without a world, client game, or dedicated server restart.
	 *
	 * @since 0.1.0
	 */
	NONE,

	/**
	 * Changes take effect after leaving and reopening a world. For a dedicated server, this normally means
	 * stopping and starting the server so that its world is loaded again.
	 *
	 * @since 0.1.0
	 */
	WORLD_RESTART,

	/**
	 * Changes take effect after restarting the client game or dedicated server process.
	 *
	 * @since 0.1.0
	 */
	GAME_RESTART
}
