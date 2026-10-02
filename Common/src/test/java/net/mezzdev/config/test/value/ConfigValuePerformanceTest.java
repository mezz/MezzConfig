package net.mezzdev.config.test.value;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaPathResolver;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigValueData;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigValuePerformanceTest {
	@ParameterizedTest
	@EnumSource(ConfigSchemaType.class)
	public void readsUseCachedStateUntilContextIsInvalidated(ConfigSchemaType type, @TempDir Path tempDir) throws IOException {
		Path first = tempDir.resolve("world/first/general.ini");
		Path second = tempDir.resolve("world/second/general.ini");
		Files.createDirectories(first.getParent());
		Files.writeString(first, "[general]\nenabled = false\n");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.of(first));
		AtomicInteger resolutions = new AtomicInteger();
		ConfigSchemaPathResolver activePathResolver = () -> {
			resolutions.incrementAndGet();
			return activePath.get();
		};
		ConfigManager manager = new ConfigManager(
			"Config Read Performance Test",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			ConfigFileWatcherSettings.serverDefaults().withEnabled(false)
		);
		ServerConfigKey serverKey = null;
		if (type == ConfigSchemaType.SERVER) {
			serverKey = new ServerConfigKey("performance_test", "general.ini");
		}
		ConfigSchemaBuilder builder = new ConfigSchemaBuilder(
			"performance_test",
			new LayeredConfigSchemaPathResolver(tempDir.resolve("client/default/general.ini"), activePathResolver),
			"mezz_config.config.performance",
			manager,
			type,
			serverKey
		);
		IConfigValue<Boolean> enabled = builder.addCategory("general").addBoolean("enabled", true).build();
		ConfigSchema schema = builder.build();
		assertFalse(enabled.get());
		int loadedResolutions = resolutions.get();
		Files.writeString(first, "[general]\nenabled = true\n");
		assertCachedReads(enabled, false);
		assertEquals(loadedResolutions, resolutions.get(), "Loaded reads must not resolve paths.");

		Files.createDirectories(second.getParent());
		Files.writeString(second, "[general]\nenabled = true\n");
		activePath.set(Optional.of(second));
		assertCachedReads(enabled, false);
		assertEquals(loadedResolutions, resolutions.get(), "Reads must wait for path invalidation.");
		manager.onWorldStarted();
		assertTrue(enabled.get());
		assertTrue(enabled.getEditorInfo().getPendingValue());

		activePath.set(Optional.empty());
		manager.onWorldStarted();
		int inactiveResolutions = resolutions.get();
		assertCachedReads(enabled, true);
		assertEquals(inactiveResolutions, resolutions.get(), "Inactive reads must not resolve paths.");

		if (type == ConfigSchemaType.SERVER) {
			schema.getServerSynchronization().orElseThrow().applyRemoteSnapshot(List.of(new ServerConfigValueData("general", "enabled", "false")));
			assertCachedReads(enabled, false);
			assertEquals(inactiveResolutions, resolutions.get(), "Remote reads must not resolve paths.");
		}
	}

	private static void assertCachedReads(IConfigValue<Boolean> enabled, boolean expectedValue) {
		for (int i = 0; i < 100; i++) {
			assertEquals(expectedValue, enabled.get());
			assertEquals(expectedValue, enabled.getEditorInfo().getPendingValue());
		}
	}
}
