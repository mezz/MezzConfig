package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.file.ConfigSerializer;

import java.util.List;

record ConfigSchemaMode(
	ConfigSerializer.Settings serializationSettings,
	boolean waitForLocalization,
	boolean synchronousFileAccess
) {
	static ConfigSchemaMode forSchema(ConfigSchemaType type) {
		return switch (type) {
			case CLIENT -> installation();
			case CLIENT_PER_WORLD -> live(true);
			case SERVER -> live(false);
		};
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
