package net.mezzdev.config.file;

import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.schema.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MezzConfigSettingsTest {
	@Test
	void registeredSettingsSchemaCreatesDefaultAndSavesEdits(@TempDir Path configRoot) throws IOException {
		// Setup: the built-in settings file disables the config file watcher.
		Path configPath = configRoot.resolve("mezz_config/client/settings.ini");
		Files.createDirectories(configPath.getParent());
		Files.write(configPath, List.of(
			"[fileWatcher]",
			"enabled = false"
		));

		// Operation: create the settings manager and obtain its registered schema.
		ConfigManager manager = MezzConfigSettings.createManager(
			"MezzConfig Settings Test File Watcher",
			configRoot,
			false
		);
		ConfigSchema schema = (ConfigSchema) manager.getSchemas().iterator().next();

		// Assertions: registering the schema creates the distributable default file.
		assertTrue(Files.exists(configRoot.resolve("mezz_config/client/default/settings.ini")));

		// Operation: enable the watcher through the registered config value.
		@SuppressWarnings("unchecked")
		IConfigValue<Boolean> enabled = (IConfigValue<Boolean>) schema.getCategories().getFirst()
			.getConfigValues().getFirst();
		assertTrue(enabled.set(true));

		// Assertions: the delayed save persists the setting to the active client file.
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			while (!Files.readString(configPath).contains("enabled = true")) {
				Thread.sleep(20);
			}
		});
	}
}
