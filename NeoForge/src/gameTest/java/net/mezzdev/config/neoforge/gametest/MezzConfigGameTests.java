package net.mezzdev.config.neoforge.gametest;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

@ForEachTest(groups = "server_config")
public final class MezzConfigGameTests {
	private static final String TEST_MOD_ID = "mezz_config_test_neoforge";

	private MezzConfigGameTests() {

	}

	@GameTest
	@EmptyTemplate
	@TestHolder(description = "Starting a dedicated-server world activates its authoritative config file.")
	public static void dedicatedServerActivatesAuthoritativeConfig(GameTestHelper helper) {
		IConfigSchema schema = Configs.getConfigManager()
			.getSchemas()
			.stream()
			.filter(candidate -> candidate.getOwnership() == ConfigOwnership.SERVER)
			.filter(candidate -> candidate.getScope() == ConfigScope.WORLD)
			.filter(candidate -> candidate.getModId().equals(TEST_MOD_ID))
			.findFirst()
			.orElseThrow(() -> failure("The NeoForge test server schema was not registered."));

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

	private static GameTestAssertException failure(String message) {
		return new GameTestAssertException(message);
	}
}
