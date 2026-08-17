package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.file.ConfigSerializer;

import java.util.List;

record ConfigSchemaMode(
	ConfigSerializer.Settings serializationSettings,
	boolean waitForLocalization,
	boolean synchronousFileAccess
) {
	static ConfigSchemaMode forSchema(ConfigOwnership ownership, ConfigScope scope) {
		if (scope == ConfigScope.INSTALLATION) {
			return installation();
		}
		return live(ownership == ConfigOwnership.CLIENT);
	}

	private static ConfigSchemaMode live(boolean waitForLocalization) {
		return new ConfigSchemaMode(
			ConfigSerializer.DEFAULT_SETTINGS,
			waitForLocalization,
			false
		);
	}

	private static ConfigSchemaMode installation() {
		ConfigSerializer.Settings settings = ConfigSerializer.Settings.withLiteralComments(List.of(
			"Config for this game installation."
		));
		return new ConfigSchemaMode(
			settings,
			false,
			true
		);
	}
}
