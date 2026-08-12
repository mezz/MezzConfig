package net.mezzdev.config.file;

import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.server.ServerConfigKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigManagerTest {
	@Test
	public void duplicateServerSchemaDoesNotReplaceOriginal() {
		ConfigManager manager = new ConfigManager(
			"Disabled Test File Watcher",
			new ConfigFileWatcherSettings(false, Duration.ofMillis(1), Duration.ofMillis(1))
		);
		ServerConfigKey key = new ServerConfigKey("test_mod", "server.ini");
		ConfigSchema original = createServerSchema(key);
		ConfigSchema duplicate = createServerSchema(key);

		manager.addConfigFile(original);
		assertThrows(IllegalArgumentException.class, () -> manager.addConfigFile(duplicate));

		assertSame(original, manager.getServerSchema(key).orElseThrow());
		assertEquals(List.of(original), List.copyOf(manager.getSchemas()));
	}

	private static ConfigSchema createServerSchema(ServerConfigKey key) {
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		category.addBoolean("enabled", true)
			.build();
		return new ConfigSchema(
			key.modId(),
			() -> Optional.empty(),
			List.of(category),
			List.of(category),
			(command, delay) -> CompletableFuture.completedFuture(null),
			ConfigSchemaType.SERVER,
			key
		);
	}
}
