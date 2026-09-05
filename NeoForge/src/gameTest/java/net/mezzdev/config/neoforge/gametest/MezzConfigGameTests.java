package net.mezzdev.config.neoforge.gametest;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ForEachTest(groups = "server_config")
public final class MezzConfigGameTests {
	private static final String TEST_MOD_ID = "mezz_config_test_neoforge";
	private static final Duration FILE_WATCHER_TEST_CHANGE_SETTLING_DELAY = Duration.ofMillis(25);

	private MezzConfigGameTests() {

	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Starting a dedicated-server world activates its authoritative config file.")
	public static void dedicatedServerActivatesAuthoritativeConfig(GameTestHelper helper) {
		IConfigSchema schema = getServerSchema();

		if (!schema.isActive()) {
			throw failure("The authoritative server schema is not active.");
		}
		Path path = schema.getPath()
			.orElseThrow(() -> failure("The authoritative server schema has no world file."));
		if (!Files.isRegularFile(path)) {
			throw failure("The authoritative server config file does not exist: " + path);
		}
		String normalizedPath = path.toString().replace('\\', '/');
		if (!normalizedPath.contains("/serverconfig/" + TEST_MOD_ID + "/")) {
			throw failure("The authoritative config resolved outside its world serverconfig directory: " + path);
		}
		helper.succeed();
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "A dedicated server keeps common client config declarations inert.")
	public static void dedicatedServerKeepsClientConfigsInert(GameTestHelper helper) {
		boolean hasClientSchema = Configs.getSchemas()
			.stream()
			.anyMatch(candidate -> candidate.getType() != ConfigSchemaType.SERVER);
		if (hasClientSchema) {
			throw failure("The dedicated server registered a client-owned config schema.");
		}
		IConfigRegistration registration = Configs.forMod(TEST_MOD_ID);
		List<IConfigSchemaBuilder> clientBuilders = List.of(
			registration.createClientSchemaBuilder(
				"dedicated-server-client.ini",
				"mezz_config_test.neoforge.client"
			),
			registration.createClientPerWorldSchemaBuilder(
				"dedicated-server-client-world.ini",
				"mezz_config_test.neoforge.client_world"
			)
		);
		for (IConfigSchemaBuilder builder : clientBuilders) {
			builder.addCategory("general")
				.addBoolean("enabled", true)
				.build();
			IConfigSchema schema = builder.build();
			if (Configs.getSchemas().contains(schema)) {
				throw failure("The dedicated server published an inert client config schema.");
			}
			if (schema.isActive() || schema.getPath().isPresent()) {
				throw failure("The dedicated server activated an inert client config schema.");
			}
		}
		var firstSortingConfig = registration.createSortingConfig(
			"dedicated-server-client.ini",
			Comparator.naturalOrder(),
			true
		);
		var secondSortingConfig = registration.createSortingConfig(
			"dedicated-server-client.ini",
			Comparator.reverseOrder(),
			false
		);
		if (!firstSortingConfig.getSortedValues(List.of("b", "a")).equals(List.of("a", "b")) ||
			!secondSortingConfig.getSortedValues(List.of("b", "a")).equals(List.of("b", "a"))
		) {
			throw failure("The dedicated server sorting configs did not remain independent and in memory.");
		}
		helper.succeed();
	}

	// The GameTest server advances ticks much faster than wall time, so this uses a test-specific 25 ms settling delay.
	@GameTest(timeoutTicks = 10000)
	@EmptyTemplate
	@TestHolder(description = "Editing an authoritative config file schedules its reload without server-tick polling.")
	public static void authoritativeFileChangeSchedulesReload(GameTestHelper helper) {
		WatchedServerConfig config = createFastWatchedServerConfig();
		Path path = config.path();
		IConfigValue<Boolean> enabled = config.enabled();
		boolean originalValue = enabled.get();
		boolean updatedValue = !originalValue;
		String originalContents;
		try {
			originalContents = Files.readString(path);
		} catch (IOException e) {
			throw failure("Failed to read the authoritative server config file: " + e.getMessage());
		}
		String originalLine = "\tenabled = " + originalValue;
		if (!originalContents.contains(originalLine)) {
			throw failure("The authoritative server config file does not contain the expected value: " + originalLine);
		}

		AtomicBoolean observedChange = new AtomicBoolean();
		Runnable removeListener = enabled.addListener(change -> {
			if (change.newValue() == updatedValue) {
				observedChange.set(true);
			}
		});
		try {
			Files.writeString(path, originalContents.replace(originalLine, "\tenabled = " + updatedValue));
		} catch (IOException e) {
			removeListener.run();
			throw failure("Failed to edit the authoritative server config file: " + e.getMessage());
		}

		helper.startSequence()
			.thenWaitUntil(() -> {
				if (!observedChange.get()) {
					throw failure("The authoritative server config file change was not loaded.");
				}
			})
			.thenExecute(() -> {
				removeListener.run();
				try {
					Files.writeString(path, originalContents);
				} catch (IOException e) {
					throw failure("Failed to restore the authoritative server config file: " + e.getMessage());
				}
			})
			.thenSucceed();
	}

	private static WatchedServerConfig createFastWatchedServerConfig() {
		Path path = getServerSchema().getPath()
			.orElseThrow(() -> failure("The authoritative server config schema has no world file."))
			.resolveSibling("file-watcher-gametest.ini");
		ConfigManager manager = new ConfigManager(
			"MezzConfig GameTest File Watcher",
			ConfigFileWatcherSettings.clientDefaults().withEnabled(false),
			new ConfigFileWatcherSettings(
				true,
				FILE_WATCHER_TEST_CHANGE_SETTLING_DELAY,
				Duration.ofSeconds(1)
			)
		);
		ConfigSchemaBuilder schemaBuilder = new ConfigSchemaBuilder(
			TEST_MOD_ID,
			new StaticConfigSchemaPathResolver(path),
			"mezz_config_test.neoforge.server",
			manager,
			ConfigSchemaType.SERVER,
			new ServerConfigKey(TEST_MOD_ID, "file-watcher-gametest.ini")
		);
		IConfigValue<Boolean> enabled = schemaBuilder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		schemaBuilder.build();
		manager.startWatching();
		return new WatchedServerConfig(path, enabled);
	}

	private static IConfigSchema getServerSchema() {
		return Configs.getSchemas()
			.stream()
			.filter(candidate -> candidate.getType() == ConfigSchemaType.SERVER)
			.filter(candidate -> candidate.getModId().equals(TEST_MOD_ID))
			.findFirst()
			.orElseThrow(() -> failure("The NeoForge test server schema was not registered."));
	}

	private static GameTestAssertException failure(String message) {
		return new GameTestAssertException(message);
	}

	private record WatchedServerConfig(Path path, IConfigValue<Boolean> enabled) {}
}
