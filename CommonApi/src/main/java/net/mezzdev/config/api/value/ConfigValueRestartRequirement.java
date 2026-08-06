package net.mezzdev.config.api.value;

/**
 * Hints whether a config value's effects require a broader restart after the value is changed.
 * <p>
 * MezzConfig stores this information for config file comments and config editors. Values still update when changed
 * through the API or loaded from the config file.
 *
 * @since 0.1.0
 */
public enum ConfigValueRestartRequirement {
	/**
	 * Changes can take effect without a world or game restart.
	 *
	 * @since 0.1.0
	 */
	NONE,

	/**
	 * Changes are expected to take effect after leaving and reopening a world.
	 *
	 * @since 0.1.0
	 */
	WORLD_RESTART,

	/**
	 * Changes are expected to take effect after restarting the game.
	 *
	 * @since 0.1.0
	 */
	GAME_RESTART
}
