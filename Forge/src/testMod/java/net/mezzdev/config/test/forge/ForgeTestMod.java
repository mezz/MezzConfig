package net.mezzdev.config.test.forge;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod(ForgeTestMod.MOD_ID)
public final class ForgeTestMod {
	public static final String MOD_ID = "mezz_config_test_forge";
	private static final String SMOKE_TEST_SUCCESS_FILE_PROPERTY = "mezzConfig.loaderSmokeTest.successFile";

	public ForgeTestMod() {
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		IConfigSchema clientSchema = registerClientConfig(registration);
		IConfigSchema serverSchema = registerServerConfig(registration);
		String successFile = System.getProperty(SMOKE_TEST_SUCCESS_FILE_PROPERTY);
		if (successFile != null) {
			MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
				MinecraftServer server = event.getServer();
				CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS)
					.execute(() -> server.execute(() -> runSmokeTest(
						clientSchema,
						serverSchema,
						server,
						Path.of(successFile)
					)));
			});
		}
	}

	private static IConfigSchema registerClientConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("forge-test.ini", "mezz_config_test.forge");
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
		IConfigSchemaBuilder schema = registration.createServerSchemaBuilder("server-test.ini", "mezz_config_test.forge.server");
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
			verifyDedicatedServerState(clientSchema, serverSchema);
			Files.createDirectories(successFile.getParent());
			Files.writeString(successFile, "passed\n");
			System.out.println("MezzConfig Forge loader smoke test passed.");
		} catch (IOException e) {
			throw new IllegalStateException("Failed to record the Forge loader smoke-test result.", e);
		} finally {
			server.halt(false);
		}
	}

	private static void verifyDedicatedServerState(IConfigSchema clientSchema, IConfigSchema serverSchema) {
		if (clientSchema.getType() != ConfigSchemaType.CLIENT ||
			clientSchema.isActive() ||
			clientSchema.getPath().isPresent() ||
			Configs.getSchemas().contains(clientSchema)
		) {
			throw new IllegalStateException("Forge activated or published a client schema on a dedicated server.");
		}
		if (clientSchema.getCategories().size() != 1 ||
			clientSchema.getCategories().getFirst().getConfigValues().size() != 4
		) {
			throw new IllegalStateException("Forge did not build the client smoke-test schema.");
		}
		if (serverSchema.getType() != ConfigSchemaType.SERVER ||
			!serverSchema.isActive() ||
			!Configs.getSchemas().contains(serverSchema)
		) {
			throw new IllegalStateException("Forge did not activate the authoritative server schema.");
		}
		Path serverPath = serverSchema.getPath()
			.orElseThrow(() -> new IllegalStateException("The Forge server schema has no active config path."));
		if (!Files.isRegularFile(serverPath) ||
			serverSchema.getCategories().size() != 1 ||
			serverSchema.getCategories().getFirst().getConfigValues().size() != 3
		) {
			throw new IllegalStateException("Forge did not load the authoritative server schema.");
		}
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}
}
