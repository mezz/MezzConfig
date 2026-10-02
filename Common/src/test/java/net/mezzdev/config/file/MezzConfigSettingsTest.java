package net.mezzdev.config.file;

import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.schema.ConfigSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MezzConfigSettingsTest {
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void missingStartupFilesUseDefaultsWithoutCreatingFiles(boolean developmentEnvironment, @TempDir Path configRoot) {
		MezzConfigSettings.StartupSettings settings = MezzConfigSettings.readStartupSettings(configRoot, developmentEnvironment);

		assertEquals(ConfigFileWatcherSettings.clientDefaults(), settings.fileWatcherSettings());
		assertEquals(developmentEnvironment, settings.logUntranslatedKeys());
		assertFalse(Files.exists(configRoot.resolve("mezz_config")));
	}

	@Test
	void startupSettingsLoadDefaultsBeforeClientOverrides(@TempDir Path configRoot) throws IOException {
		Path defaultPath = configRoot.resolve("mezz_config/client/default/settings.ini");
		Files.createDirectories(defaultPath.getParent());
		String defaultContents = """
			[fileWatcher]
			enabled = false
			changeSettlingDelayMilliseconds = 75
			missingDirectoryRetryIntervalMilliseconds = 3000
			[logging]
			logUntranslatedKeys = true
			""";
		Files.writeString(defaultPath, defaultContents);

		MezzConfigSettings.StartupSettings defaults = MezzConfigSettings.readStartupSettings(configRoot, false);
		assertEquals(new ConfigFileWatcherSettings(false, Duration.ofMillis(75), Duration.ofSeconds(3)), defaults.fileWatcherSettings());
		assertTrue(defaults.logUntranslatedKeys());

		Path clientPath = configRoot.resolve("mezz_config/client/settings.ini");
		String clientContents = """
			[fileWatcher]
			enabled = true
			changeSettlingDelayMilliseconds = 25
			[logging]
			logUntranslatedKeys = false
			""";
		Files.writeString(clientPath, clientContents);

		MezzConfigSettings.StartupSettings settings = MezzConfigSettings.readStartupSettings(configRoot, false);
		assertEquals(new ConfigFileWatcherSettings(true, Duration.ofMillis(25), Duration.ofSeconds(3)), settings.fileWatcherSettings());
		assertFalse(settings.logUntranslatedKeys());
		assertEquals(defaultContents, Files.readString(defaultPath));
		assertEquals(clientContents, Files.readString(clientPath));
	}

	@Test
	void malformedStartupSettingsKeepValidValuesAndUseDefaultFallbacks(@TempDir Path configRoot) throws IOException {
		Path defaultPath = configRoot.resolve("mezz_config/client/default/settings.ini");
		Files.createDirectories(defaultPath.getParent());
		Files.writeString(defaultPath, "[fileWatcher]\nchangeSettlingDelayMilliseconds = 75\n");
		Path clientPath = configRoot.resolve("mezz_config/client/settings.ini");
		String malformedContents = "[fileWatcher]\nenabled = false\nchangeSettlingDelayMilliseconds = invalid\n";
		Files.writeString(clientPath, malformedContents);

		MezzConfigSettings.StartupSettings settings = MezzConfigSettings.readStartupSettings(configRoot, false);

		assertFalse(settings.fileWatcherSettings().enabled());
		assertEquals(Duration.ofMillis(75), settings.fileWatcherSettings().changeSettlingDelay());
		assertEquals(malformedContents, Files.readString(ConfigFileUtil.getBackupPath(clientPath, 1)));
		assertFalse(Files.readString(clientPath).contains("invalid"));
		assertEquals(settings, MezzConfigSettings.readStartupSettings(configRoot, false));
	}

	@Test
	void unreadableStartupSettingsFailBeforeCreatingManager(@TempDir Path configRoot) throws IOException {
		Files.createDirectories(configRoot.resolve("mezz_config/client/settings.ini"));

		assertThrows(UncheckedIOException.class, () -> MezzConfigSettings.createManager("Unused Watcher", configRoot, false));
	}

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
