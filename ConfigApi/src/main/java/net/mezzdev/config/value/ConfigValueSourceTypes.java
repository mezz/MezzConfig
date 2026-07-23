package net.mezzdev.config.value;

/**
 * Standard config value source types used by config screens.
 *
 * @since 19.39.0
 */
public final class ConfigValueSourceTypes {
	private static final String CONFIG_ID = "mezz_config";

	public static final ConfigValueSourceType<ConfigValueSources.IValues> VALUES = ConfigValueSourceType.create(CONFIG_ID, "values");
	public static final ConfigValueSourceType<ConfigValueSources.IKeyMappings> KEY_MAPPINGS = ConfigValueSourceType.create(CONFIG_ID, "key_mappings");

	private ConfigValueSourceTypes() {

	}
}
