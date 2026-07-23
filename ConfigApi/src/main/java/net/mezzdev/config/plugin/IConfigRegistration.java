package net.mezzdev.config.plugin;

import net.mezzdev.config.files.IConfigManager;
import net.mezzdev.config.schema.IConfigEditableSchema;
import net.mezzdev.config.schema.IConfigSchemaBuilder;
import net.minecraft.network.chat.Component;

/**
 * Registration for config files provided by one config plugin.
 *
 * @since 19.39.0
 */
public interface IConfigRegistration {
	/**
	 * Create a config schema builder.
	 * The file is resolved inside the config directory owned by this plugin's mod id.
	 *
	 * @param configFileName relative file name for the config file
	 * @param localizationPath translation key prefix for the config file
	 *
	 * @since 19.39.0
	 */
	IConfigSchemaBuilder createSchemaBuilder(String configFileName, String localizationPath);

	/**
	 * Register a config screen with the default config screen title.
	 * The restart handler is called when applying saved changes requires the owner mod to restart or reload.
	 *
	 * @param schema config schema to display
	 * @param restartHandler handles saved changes that require the owner mod to restart or reload
	 *
	 * @since 19.39.0
	 */
	default void registerConfigScreen(IConfigEditableSchema schema, Runnable restartHandler) {
		registerConfigScreen(Component.translatable("mezz_config.config.screen.title"), schema, restartHandler);
	}

	/**
	 * Register a config screen.
	 * The restart handler is called when applying saved changes requires the owner mod to restart or reload.
	 *
	 * @param title the title shown at the top of the config screen
	 * @param schema config schema to display
	 * @param restartHandler handles saved changes that require the owner mod to restart or reload
	 *
	 * @since 19.39.0
	 */
	void registerConfigScreen(Component title, IConfigEditableSchema schema, Runnable restartHandler);

	/**
	 * Get the config manager that is receiving registered files.
	 *
	 * @since 19.39.0
	 */
	IConfigManager getConfigManager();
}
