package net.mezzdev.config.neoforge.gametest;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.IConfigValue;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@ForEachTest(groups = "server_config")
public final class MezzConfigGameTests {
	private static final String TEST_MOD_ID = "mezz_config_test_neoforge";

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

	// The GameTest server advances ticks much faster than wall time, but the file watcher intentionally settles for 500 ms.
	@GameTest(timeoutTicks = 10000)
	@EmptyTemplate
	@TestHolder(description = "Editing an authoritative config file schedules its reload without server-tick polling.")
	public static void authoritativeFileChangeSchedulesReload(GameTestHelper helper) {
		IConfigSchema schema = getServerSchema();
		Path path = schema.getPath()
			.orElseThrow(() -> failure("The authoritative server config schema has no world file."));
		IConfigValue<Boolean> enabled = getBooleanValue(schema, "enabled");
		boolean originalValue = enabled.getValue();
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

	private static IConfigSchema getServerSchema() {
		return Configs.getConfigManager()
			.getSchemas()
			.stream()
			.filter(candidate -> candidate.getOwnership() == ConfigOwnership.SERVER)
			.filter(candidate -> candidate.getScope() == ConfigScope.WORLD)
			.filter(candidate -> candidate.getModId().equals(TEST_MOD_ID))
			.findFirst()
			.orElseThrow(() -> failure("The NeoForge test server schema was not registered."));
	}

	private static IConfigValue<Boolean> getBooleanValue(IConfigSchema schema, String name) {
		IConfigValue<?> value = schema.getCategories()
			.stream()
			.flatMap(category -> category.getConfigValues().stream())
			.filter(candidate -> candidate.getName().equals(name))
			.findFirst()
			.orElseThrow(() -> failure("The server config schema does not contain the expected value: " + name));
		if (!(value.getDefaultValue() instanceof Boolean)) {
			throw failure("The server config value is not a boolean: " + name);
		}
		@SuppressWarnings("unchecked")
		IConfigValue<Boolean> booleanValue = (IConfigValue<Boolean>) value;
		return booleanValue;
	}

	private static GameTestAssertException failure(String message) {
		return new GameTestAssertException(message);
	}
}
