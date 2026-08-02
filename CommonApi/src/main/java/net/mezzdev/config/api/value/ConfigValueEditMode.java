package net.mezzdev.config.api.value;

/**
 * Hints how config editors should save edits for a config value.
 * <p>
 * MezzConfig stores this information but does not enforce it. Editors can use it to choose between immediate edits,
 * batched edits, and edits that require a full game restart after they are saved.
 *
 * @since 0.1.0
 */
public enum ConfigValueEditMode {
	/**
	 * Editors can save changes as soon as the user makes them.
	 *
	 * @since 0.1.0
	 */
	IMMEDIATE,

	/**
	 * Editors should stage changes and save them together when the user applies pending changes.
	 *
	 * @since 0.1.0
	 */
	BATCH,

	/**
	 * Editors should stage changes and tell the user that saving them requires a full game restart.
	 *
	 * @since 0.1.0
	 */
	RESTART
}
