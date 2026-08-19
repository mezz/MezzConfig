package net.mezzdev.config.test.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class FabricTestMod implements ModInitializer {
	private static final String MOD_ID = "mezz_config_test_fabric";
	private static final String SMOKE_TEST_SUCCESS_FILE_PROPERTY = "mezzConfig.loaderSmokeTest.successFile";

	@Override
	public void onInitialize() {
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		IConfigSchema clientSchema = registerClientConfig(registration);
		IConfigSchema serverSchema = registerServerConfig(registration);
		String successFile = System.getProperty(SMOKE_TEST_SUCCESS_FILE_PROPERTY);
		if (successFile != null) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS)
				.execute(() -> server.execute(() -> runSmokeTest(
					clientSchema,
					serverSchema,
					server,
					Path.of(successFile)
				))));
		}
	}

	private static IConfigSchema registerClientConfig(IConfigRegistration registration) {
		IConfigSchemaBuilder schema = registration.createClientSchemaBuilder("fabric-test.ini", "mezz_config_test.fabric");
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
		IConfigSchemaBuilder schema = registration.createServerSchemaBuilder("server-test.ini", "mezz_config_test.fabric.server");
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
			System.out.println("MezzConfig Fabric loader smoke test passed.");
		} catch (IOException e) {
			throw new IllegalStateException("Failed to record the Fabric loader smoke-test result.", e);
		} finally {
			server.halt(false);
		}
	}

	private static void verifyDedicatedServerState(IConfigSchema clientSchema, IConfigSchema serverSchema) {
		if (clientSchema.getType() != ConfigSchemaType.CLIENT ||
			clientSchema.isActive() ||
			clientSchema.canEdit() ||
			clientSchema.getPath().isPresent() ||
			Configs.getSchemas().contains(clientSchema)
		) {
			throw new IllegalStateException("Fabric activated or published a client schema on a dedicated server.");
		}
		if (clientSchema.getCategories().size() != 1 ||
			clientSchema.getCategories().getFirst().getConfigValues().size() != 4
		) {
			throw new IllegalStateException("Fabric did not build the client smoke-test schema.");
		}
		if (serverSchema.getType() != ConfigSchemaType.SERVER ||
			!serverSchema.isActive() ||
			!serverSchema.canEdit() ||
			!Configs.getSchemas().contains(serverSchema)
		) {
			throw new IllegalStateException("Fabric did not activate the authoritative server schema.");
		}
		Path serverPath = serverSchema.getPath()
			.orElseThrow(() -> new IllegalStateException("The Fabric server schema has no active config path."));
		if (!Files.isRegularFile(serverPath) ||
			serverSchema.getCategories().size() != 1 ||
			serverSchema.getCategories().getFirst().getConfigValues().size() != 3
		) {
			throw new IllegalStateException("Fabric did not load the authoritative server schema.");
		}
	}

	private enum TestMode {
		STANDARD,
		ADVANCED
	}
}
