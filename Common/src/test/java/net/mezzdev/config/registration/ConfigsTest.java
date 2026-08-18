package net.mezzdev.config.registration;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigOwnership;
import net.mezzdev.config.api.schema.ConfigScope;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.file.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigsTest {
	private static final String MOD_ID = "registration_test";
	private static final String FILE_NAME = "shared.ini";
	private static final int MAX_CONFIG_FILE_BYTES = 4 * 1024 * 1024;

	@Test
	public void clientAndServerSchemasLoadSynchronouslyFromIndependentPaths(@TempDir Path configRoot) throws IOException {
		Path clientPath = getClientPath(configRoot);
		Path serverPath = getServerPath(configRoot);
		writeEnabled(clientPath, false);
		writeEnabled(serverPath, true);
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);

		TestSchema client = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		TestSchema server = createSchema(registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"), false);

		assertFalse(client.enabled().getValue());
		assertTrue(server.enabled().getValue());
		assertEquals(ConfigOwnership.CLIENT, client.schema().getOwnership());
		assertEquals(ConfigOwnership.SERVER, server.schema().getOwnership());
		assertEquals(ConfigScope.INSTALLATION, client.schema().getScope());
		assertEquals(ConfigScope.INSTALLATION, server.schema().getScope());
		assertEquals(clientPath, client.schema().getPath().orElseThrow());
		assertEquals(serverPath, server.schema().getPath().orElseThrow());
		assertFalse(clientPath.equals(serverPath));
		assertTrue(
			getConfigManager().getServerSchemas().stream()
				.noneMatch(schema -> schema == server.schema()),
			"Installation schemas must not enter server synchronization."
		);
	}

	@Test
	public void gameRestartRequirementKeepsSavedValuePending(@TempDir Path configRoot) throws IOException {
		Path path = getClientPath(configRoot);
		writeEnabled(path, false);
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		TestSchema config = createSchema(
			registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"),
			true,
			ConfigValueRestartRequirement.GAME_RESTART
		);

		assertTrue(config.enabled().set(true));

		assertFalse(config.enabled().getValue());
		assertTrue(config.enabled().getPendingValue());
		awaitFileContent(path, "enabled = true");
	}

	@Test
	public void externalChangesReloadInstallationValues(@TempDir Path configRoot) throws IOException {
		Path path = getServerPath(configRoot);
		writeEnabled(path, true);
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		TestSchema config = createSchema(registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"), false);

		writeEnabled(path, false);

		awaitValue(config.enabled(), false);
		assertFalse(config.enabled().getPendingValue());
	}

	@Test
	public void restartRequirementsStayPerValueAndMissingFilesAreCreatedSynchronously(@TempDir Path configRoot) {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		assertEquals(ConfigValueRestartRequirement.NONE, config.enabled().getRestartRequirement());
		assertTrue(Files.exists(getClientPath(configRoot)));
	}

	@Test
	public void worldScopeMakesServerSchemaContextualAndSynchronized(@TempDir Path configRoot) {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		TestSchema config = createSchema(
			registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server")
				.setScope(ConfigScope.WORLD),
			true
		);

		assertEquals(ConfigOwnership.SERVER, config.schema().getOwnership());
		assertEquals(ConfigScope.WORLD, config.schema().getScope());
		assertFalse(config.schema().isActive());
		assertTrue(getConfigManager().getServerSchemas().contains(config.schema()));
		assertFalse(Files.exists(getServerWorldDefaultPath(configRoot)));
	}

	@Test
	public void duplicateInstallationIdentityIsRejected(@TempDir Path configRoot) {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		TestSchema original = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);
		assertTrue(Configs.getSchemas().contains(original.schema()));
	}

	@Test
	public void clientInstallationAndWorldSchemasCannotShareDefaultPath(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		Path path = getClientPath(configRoot).getParent()
			.resolve("world/default")
			.resolve(FILE_NAME);
		createSchema(
			registration.createClientSchemaBuilder("world/default/" + FILE_NAME, "registration_test.client"),
			true
		);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client_world")
					.setScope(ConfigScope.WORLD),
				false
			)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void serverInstallationAndWorldSchemasCannotShareDefaultPath(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		Path path = getServerWorldDefaultPath(configRoot);
		createSchema(
			registration.createServerSchemaBuilder("world/default/" + FILE_NAME, "registration_test.server"),
			true
		);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server_world")
					.setScope(ConfigScope.WORLD),
				false
			)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void sortingConfigRejectsSchemaCollisionBeforeCreatingFile(@TempDir Path configRoot) {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		Path path = getClientPath(configRoot);
		registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);

		assertFalse(Files.exists(path));
	}

	@Test
	public void schemaRejectsSortingCollisionWithoutModifyingFile(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		Path path = getClientPath(configRoot);
		createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void conventionalAndExplicitRootsUseTheSamePathIdentity(@TempDir Path tempDir) {
		String fileName = "path-identity/" + tempDir.getFileName() + ".ini";
		Path conventionalRoot = Path.of("build", "test-config");
		IConfigRegistration conventional = Configs.forMod(MOD_ID);
		IConfigRegistration explicit = Configs.forMod(conventionalRoot.toAbsolutePath(), MOD_ID);
		conventional.createSortingConfig(fileName, Comparator.naturalOrder(), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> explicit.createSortingConfig(fileName, Comparator.naturalOrder(), true)
		);

		assertFalse(Files.exists(conventionalRoot.resolve(MOD_ID).resolve("client").resolve(fileName)));
	}

	@Test
	public void modAndFilePathsCannotEscapeTheirOwnedDirectories(@TempDir Path configRoot) {
		assertThrows(IllegalArgumentException.class, () -> Configs.forMod(configRoot, "../outside"));
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createClientSchemaBuilder("../outside.ini", "registration_test.client")
		);
	}

	@Test
	public void malformedInstallationFileUsesNormalRecovery(@TempDir Path configRoot) throws IOException {
		Path path = getServerPath(configRoot);
		writeFile(path, "[general]\nenabled = true\nbounded = not-an-integer\nunknown = true\n");
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);

		TestSchema config = createSchema(registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"), false);

		assertTrue(config.enabled().getValue());
		assertEquals(0, config.bounded().getValue());
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
		String corrected = Files.readString(path);
		assertTrue(corrected.contains("Config for this game installation"));
		assertTrue(corrected.contains("enabled = true"));
		assertTrue(corrected.contains("bounded = 0"));
		assertFalse(corrected.contains("unknown ="));
	}

	@Test
	public void invalidUtf8AndOversizedFilesUseNormalBoundedRecovery(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		Path clientPath = getClientPath(configRoot);
		Files.createDirectories(clientPath.getParent());
		byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
		Files.write(clientPath, invalidUtf8);
		Path serverPath = getServerPath(configRoot);
		Files.createDirectories(serverPath.getParent());
		Files.write(serverPath, new byte[MAX_CONFIG_FILE_BYTES + 1]);

		createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		createSchema(registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"), false);

		assertEquals(invalidUtf8.length, Files.size(ConfigFileUtil.getBackupPath(clientPath, 1)));
		assertTrue(Files.readString(clientPath).contains("enabled = true"));
		assertEquals(MAX_CONFIG_FILE_BYTES + 1, Files.size(ConfigFileUtil.getBackupPath(serverPath, 1)));
		assertTrue(Files.size(serverPath) < MAX_CONFIG_FILE_BYTES);
	}

	@Test
	public void synchronousReadFailureDoesNotPublishSchema(@TempDir Path configRoot) throws IOException {
		Path path = getClientPath(configRoot);
		Files.createDirectories(path);
		IConfigRegistration registration = Configs.forMod(configRoot, MOD_ID);
		int schemaCount = Configs.getSchemas().size();

		assertThrows(
			UncheckedIOException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);

		assertEquals(schemaCount, Configs.getSchemas().size());
		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	private static TestSchema createSchema(IConfigSchemaBuilder builder, boolean defaultEnabled) {
		return createSchema(builder, defaultEnabled, ConfigValueRestartRequirement.NONE);
	}

	private static ConfigManager getConfigManager() {
		return ConfigProvider.getConfigManager();
	}

	private static TestSchema createSchema(
		IConfigSchemaBuilder builder,
		boolean defaultEnabled,
		ConfigValueRestartRequirement restartRequirement
	) {
		var general = builder.addCategory("general");
		IConfigValue<Boolean> enabled = general.addBoolean("enabled", defaultEnabled)
			.setRestartRequirement(restartRequirement)
			.build();
		IConfigValue<Integer> bounded = general.addInteger("bounded", 0, 0, 1)
			.build();
		IConfigSchema schema = builder.build();
		return new TestSchema(schema, enabled, bounded);
	}

	private static Path getClientPath(Path configRoot) {
		return configRoot.resolve(MOD_ID).resolve("client").resolve(FILE_NAME);
	}

	private static Path getServerPath(Path configRoot) {
		return configRoot.resolve(MOD_ID).resolve("server").resolve(FILE_NAME);
	}

	private static Path getServerWorldDefaultPath(Path configRoot) {
		return configRoot.resolve(MOD_ID).resolve("server/world/default").resolve(FILE_NAME);
	}

	private static void writeEnabled(Path path, boolean enabled) throws IOException {
		writeFile(path, "[general]\nenabled = " + enabled + "\n");
	}

	private static void writeFile(Path path, String contents) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, contents);
	}

	private static void awaitFileContent(Path path, String expected) throws IOException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (Files.readString(path).contains(expected)) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for config save.", e);
			}
		}
		throw new AssertionError("Config file was not saved with: " + expected);
	}

	private static <T> void awaitValue(IConfigValue<T> value, T expected) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (expected.equals(value.getValue())) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for config reload.", e);
			}
		}
		throw new AssertionError("Config value was not reloaded with: " + expected);
	}

	private record TestSchema(
		IConfigSchema schema,
		IConfigValue<Boolean> enabled,
		IConfigValue<Integer> bounded
	) {}
}
