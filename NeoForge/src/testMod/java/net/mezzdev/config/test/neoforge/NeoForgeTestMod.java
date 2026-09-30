package net.mezzdev.config.test.neoforge;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mod(NeoForgeTestMod.MOD_ID)
public final class NeoForgeTestMod {
	public static final String MOD_ID = "mezz_config_test_neoforge";
	private static final String SMOKE_TEST_SUCCESS_FILE_PROPERTY = "mezzConfig.loaderSmokeTest.successFile";

	public NeoForgeTestMod() {
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		IConfigSchema clientSchema = registerClientConfig(registration);
		IConfigSchema serverSchema = registerServerConfig(registration);
		String successFile = System.getProperty(SMOKE_TEST_SUCCESS_FILE_PROPERTY);
		if (successFile != null) {
			NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
				MinecraftServer server = event.getServer();
				CompletableFuture.runAsync(() -> server.execute(() -> runSmokeTest(
					clientSchema,
					serverSchema,
					server,
					Path.of(successFile)
				)));
			});
		}
	}

	private static IConfigSchema registerClientConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("neoforge-test.ini", "mezz_config_test.neoforge");
		IConfigCategoryBuilder general = schema.addCategory("general");
		general.addBoolean("enabled", true)
			.build();
		general.addInteger("maxEntries", 16, 0, 64)
			.build();
		general.addEnum("mode", TestMode.STANDARD)
			.build();
		general.addEnumList("modes", List.of(TestMode.STANDARD), TestMode.class)
			.build();
		return schema.build();
	}

	private static IConfigSchema registerServerConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createServerSchemaBuilder("server-test.ini", "mezz_config_test.neoforge.server");
		IConfigCategoryBuilder general = schema.addCategory("general");
		general.addBoolean("enabled", true)
			.build();
		general.addInteger("maxEntries", 16, 0, 64)
			.setRestartRequirement(ConfigValueRestartRequirement.WORLD_RESTART)
			.build();
		general.addEnum("mode", TestMode.STANDARD)
			.setRestartRequirement(ConfigValueRestartRequirement.GAME_RESTART)
			.build();
		return schema.build();
	}

	private static void runSmokeTest(
		IConfigSchema clientSchema,
		IConfigSchema serverSchema,
		MinecraftServer server,
		Path successFile
	) {
		try {
			ModList modList = ModList.get();
			var configMod = modList.getModContainerById("mezz_config").orElseThrow();
			String version = configMod.getModInfo().getVersion().toString();
			String expectedVersion = System.getProperty("mezzConfig.loaderSmokeTest.expectedVersion");
			if (!version.equals(expectedVersion)) {
				throw new IllegalStateException("Expected MezzConfig " + expectedVersion + ", loaded " + version);
			}
			long configCopies = modList.getMods().stream().filter(mod -> mod.getModId().equals("mezz_config")).count();
			if (configCopies != 1 || modList.getModFileById("mezz_config").getFile().getDiscoveryAttributes().parent() == null) {
				throw new IllegalStateException("Expected exactly one MezzConfig loaded from a nested jar.");
			}
			if (Boolean.getBoolean("mezzConfig.loaderSmokeTest.requireJei") && !modList.isLoaded("jei")) {
				throw new IllegalStateException("JEI was not loaded for the coexistence smoke test.");
			}
			if (clientSchema.getType() != ConfigSchemaType.CLIENT || clientSchema.isActive() ||
				clientSchema.getPath().isPresent() || Configs.getSchemas().contains(clientSchema)
			) {
				throw new IllegalStateException("NeoForge activated or published a client schema on a dedicated server.");
			}
			if (serverSchema.getType() != ConfigSchemaType.SERVER || !serverSchema.isActive() ||
				!Configs.getSchemas().contains(serverSchema) ||
				!Files.isRegularFile(serverSchema.getPath().orElseThrow())
			) {
				throw new IllegalStateException("NeoForge did not load the authoritative server schema.");
			}
			Files.createDirectories(successFile.getParent());
			Files.writeString(successFile, "MezzConfig " + version + "; JEI loaded: " + modList.isLoaded("jei") + "\n");
			System.out.println("MezzConfig NeoForge standalone loader smoke test passed: " + version);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to record the NeoForge loader smoke-test result.", e);
		} finally {
			server.halt(false);
		}
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}
}
