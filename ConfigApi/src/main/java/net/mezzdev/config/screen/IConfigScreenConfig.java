package net.mezzdev.config.screen;

import net.mezzdev.config.schema.IConfigEditableSchema;
import net.minecraft.network.chat.Component;

/**
 * Config screen metadata registered by a config plugin.
 *
 * @since 19.39.0
 */
public interface IConfigScreenConfig {
	/**
	 * The mod id that owns this config screen.
	 *
	 * @since 19.39.0
	 */
	String getModId();

	/**
	 * The title shown at the top of the config screen.
	 *
	 * @since 19.39.0
	 */
	Component getTitle();

	/**
	 * The editable schema shown by this config screen.
	 *
	 * @since 19.39.0
	 */
	IConfigEditableSchema getSchema();

	/**
	 * Called when applying saved changes requires the owner mod to restart or reload.
	 *
	 * @since 19.39.0
	 */
	void onRestartRequired();
}
