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
		Path configPath = configRoot.resolve("mezz_config/client/settings.ini");
		Files.createDirectories(configPath.getParent());
		Files.write(configPath, List.of(
			"[fileWatcher]",
			"enabled = false"
		));

		ConfigManager manager = MezzConfigSettings.createManager(
			"MezzConfig Settings Test File Watcher",
			configRoot,
			false
		);
		ConfigSchema schema = (ConfigSchema) manager.getSchemas().iterator().next();

		assertTrue(Files.exists(configRoot.resolve("mezz_config/client/default/settings.ini")));

		@SuppressWarnings("unchecked")
		IConfigValue<Boolean> enabled = (IConfigValue<Boolean>) schema.getCategories().getFirst()
			.getConfigValues().getFirst();
		assertTrue(enabled.set(true));
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			while (!Files.readString(configPath).contains("enabled = true")) {
				Thread.sleep(20);
			}
		});
	}
}
