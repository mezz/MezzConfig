package net.mezzdev.config.registration;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigsTest {
	private static final String MOD_ID = "registration_test";
	private static final String FILE_NAME = "shared.ini";
	private static final int MAX_CONFIG_FILE_BYTES = 4 * 1024 * 1024;
	private static final IConfigValueSerializer<Integer> INTEGER_SERIALIZER = new IConfigValueSerializer<>() {
		@Override
		public String serialize(Integer value) {
			return value.toString();
		}

		@Override
		public IDeserializeResult<Integer> deserialize(String string) {
			try {
				return IDeserializeResult.success(Integer.parseInt(string));
			} catch (NumberFormatException e) {
				return IDeserializeResult.failure("Expected an integer.");
			}
		}

		@Override
		public boolean isValid(Integer value) {
			return value != null;
		}

		@Override
		public String getValidValuesDescription() {
			return "Any integer";
		}
	};

	@Test
	public void factoriesCreateCompleteClientAndServerSchemaTypes(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = createRegistration(configRoot);
		Path clientPath = getClientPath(configRoot, "client.ini");
		Path explicitPath = configRoot.resolve("outside-owned-layout/explicit.ini");
		writeEnabled(clientPath, false);
		writeEnabled(explicitPath, true);

		TestSchema client = createSchema(
			registration.createClientSchemaBuilder("client.ini", "registration_test.client"),
			true
		);
		TestSchema explicit = createSchema(
			registration.createClientSchemaBuilderAtLocation(explicitPath, "registration_test.explicit"),
			false
		);
		TestSchema server = createSchema(
			registration.createServerSchemaBuilder("server.ini", "registration_test.server"),
			true
		);

		assertEquals(ConfigSchemaType.CLIENT, client.schema().getType());
		assertEquals(ConfigSchemaType.CLIENT, explicit.schema().getType());
		assertEquals(ConfigSchemaType.SERVER, server.schema().getType());
		assertEquals("client.ini", client.schema().getId());
		assertEquals(explicitPath.toString().replace('\\', '/'), explicit.schema().getId());
		assertEquals("server.ini", server.schema().getId());
		assertTrue(client.schema().isActive());
		assertFalse(client.enabled().getValue());
		assertTrue(explicit.schema().isActive());
		assertTrue(explicit.enabled().getValue());
		assertEquals(clientPath, client.schema().getPath().orElseThrow());
		assertEquals(explicitPath, explicit.schema().getPath().orElseThrow());
		assertFalse(server.schema().isActive());
		assertEquals(Optional.empty(), server.schema().getPath());
		assertTrue(getConfigManager().getServerSchemas().contains(server.schema()));
	}

	@Test
	public void gameRestartRequirementKeepsSavedValuePending(@TempDir Path configRoot) throws IOException {
		Path path = getClientPath(configRoot, FILE_NAME);
		writeEnabled(path, false);
		IConfigRegistration registration = createRegistration(configRoot);
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
	public void externalChangesReloadClientValues(@TempDir Path configRoot) throws IOException {
		Path path = getClientPath(configRoot, FILE_NAME);
		writeEnabled(path, true);
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), false);

		writeEnabled(path, false);

		awaitValue(config.enabled(), false);
		assertFalse(config.enabled().getPendingValue());
	}

	@Test
	public void missingClientFilesAreCreatedSynchronously(@TempDir Path configRoot) {
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		assertEquals(ConfigValueRestartRequirement.NONE, config.enabled().getRestartRequirement());
		assertTrue(Files.exists(getClientPath(configRoot, FILE_NAME)));
	}

	@Test
	public void serverFactoryCreatesContextualSynchronizedSchema(@TempDir Path configRoot) {
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(
			registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"),
			true
		);

		assertEquals(ConfigSchemaType.SERVER, config.schema().getType());
		assertFalse(config.schema().isActive());
		assertTrue(getConfigManager().getServerSchemas().contains(config.schema()));
		assertFalse(Files.exists(getServerWorldDefaultPath(configRoot, FILE_NAME)));
	}

	@Test
	public void duplicateClientIdentityIsRejected(@TempDir Path configRoot) {
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema original = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);
		assertTrue(Configs.getSchemas().contains(original.schema()));
	}

	@Test
	public void clientAndClientWorldSchemasCannotShareDefaultPath(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, "world/default/" + FILE_NAME);
		createSchema(
			registration.createClientSchemaBuilder("world/default/" + FILE_NAME, "registration_test.client"),
			true
		);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientPerWorldSchemaBuilder(FILE_NAME, "registration_test.client_world"),
				false
			)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void sortingConfigRejectsSchemaCollisionBeforeCreatingFile(@TempDir Path configRoot) {
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, FILE_NAME);
		registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);

		assertFalse(Files.exists(path));
	}

	@Test
	public void sortingFactoriesSupportStringAndGenericValues(@TempDir Path configRoot) {
		IConfigRegistration registration = createRegistration(configRoot);
		ISortingConfig<String> strings = registration.createSortingConfig(
			"strings.txt",
			Comparator.reverseOrder(),
			false
		);
		ISortingConfig<Integer> integers = registration.createSortingConfig(
			"integers.txt",
			INTEGER_SERIALIZER,
			Comparator.naturalOrder(),
			true
		);

		assertEquals(List.of("second", "first"), strings.getSortedValues(List.of("first", "second")));
		assertEquals(List.of(1, 2, 3), integers.getSortedValues(List.of(3, 1, 2)));
		assertTrue(integers.setSortedValues(List.of(1, 2, 3), List.of(3, 1)));
		assertEquals(List.of(3, 1, 4), integers.getSortedValues(List.of(1, 2, 3, 4)));
		assertFalse(integers.isVisible(List.of(1, 2, 3, 4), 2));
	}

	@Test
	public void schemaRejectsSortingCollisionWithoutModifyingFile(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, FILE_NAME);
		createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		String originalContents = Files.readString(path);

		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true)
		);

		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void automaticAndExplicitLocationsUseTheSamePathIdentity(@TempDir Path tempDir) {
		String fileName = "path-identity/" + tempDir.getFileName() + ".ini";
		Path conventionalRoot = Path.of("build", "test-config").toAbsolutePath().normalize();
		Path conventionalPath = getClientPath(conventionalRoot, fileName);
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		registration.createSortingConfig(fileName, Comparator.naturalOrder(), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientSchemaBuilderAtLocation(conventionalPath, "registration_test.client"),
				true
			)
		);

		assertFalse(Files.exists(conventionalPath));
	}

	@Test
	public void automaticNamesCannotEscapeButExplicitLocationsMayBeAnywhere(@TempDir Path configRoot) {
		assertThrows(IllegalArgumentException.class, () -> Configs.forMod("../outside"));
		IConfigRegistration registration = createRegistration(configRoot);
		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createClientSchemaBuilder("../outside.ini", "registration_test.client")
		);

		Path explicitPath = configRoot.resolve("outside-owned-layout/settings.ini");
		TestSchema explicit = createSchema(
			registration.createClientSchemaBuilderAtLocation(explicitPath, "registration_test.explicit"),
			true
		);
		assertEquals(explicitPath, explicit.schema().getPath().orElseThrow());
	}

	@Test
	public void relativeExplicitLocationIsCapturedAsNormalizedAbsolutePath(@TempDir Path tempDir) {
		Path absolutePath = tempDir.resolve("nested/settings.ini").toAbsolutePath().normalize();
		Path relativePath = Path.of("").toAbsolutePath().normalize().relativize(absolutePath);
		IConfigRegistration registration = createRegistration(tempDir.resolve("automatic-root"));

		TestSchema config = createSchema(
			registration.createClientSchemaBuilderAtLocation(relativePath, "registration_test.explicit"),
			true
		);

		assertEquals(absolutePath, config.schema().getPath().orElseThrow());
		assertTrue(Files.isRegularFile(absolutePath));
	}

	@Test
	public void malformedClientFileUsesNormalRecovery(@TempDir Path configRoot) throws IOException {
		Path path = getClientPath(configRoot, FILE_NAME);
		writeFile(path, "[general]\nenabled = true\nbounded = not-an-integer\nunknown = true\n");
		IConfigRegistration registration = createRegistration(configRoot);

		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), false);

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
	public void invalidUtf8AndOversizedExplicitFilesUseNormalBoundedRecovery(@TempDir Path configRoot) throws IOException {
		IConfigRegistration registration = createRegistration(configRoot);
		Path invalidPath = configRoot.resolve("explicit/invalid.ini");
		Files.createDirectories(invalidPath.getParent());
		byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
		Files.write(invalidPath, invalidUtf8);
		Path oversizedPath = configRoot.resolve("explicit/oversized.ini");
		Files.write(oversizedPath, new byte[MAX_CONFIG_FILE_BYTES + 1]);

		createSchema(registration.createClientSchemaBuilderAtLocation(invalidPath, "registration_test.invalid"), true);
		createSchema(registration.createClientSchemaBuilderAtLocation(oversizedPath, "registration_test.oversized"), false);

		assertEquals(invalidUtf8.length, Files.size(ConfigFileUtil.getBackupPath(invalidPath, 1)));
		assertTrue(Files.readString(invalidPath).contains("enabled = true"));
		assertEquals(MAX_CONFIG_FILE_BYTES + 1, Files.size(ConfigFileUtil.getBackupPath(oversizedPath, 1)));
		assertTrue(Files.size(oversizedPath) < MAX_CONFIG_FILE_BYTES);
	}

	@Test
	public void synchronousReadFailureDoesNotPublishSchema(@TempDir Path configRoot) throws IOException {
		Path path = configRoot.resolve("explicit/client.ini");
		Files.createDirectories(path);
		IConfigRegistration registration = createRegistration(configRoot);
		int schemaCount = Configs.getSchemas().size();

		assertThrows(
			UncheckedIOException.class,
			() -> createSchema(
				registration.createClientSchemaBuilderAtLocation(path, "registration_test.client"),
				true
			)
		);

		assertEquals(schemaCount, Configs.getSchemas().size());
		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	private static IConfigRegistration createRegistration(Path configRoot) {
		return ConfigProvider.createRegistration(configRoot, MOD_ID);
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

	private static Path getClientPath(Path configRoot, String fileName) {
		return configRoot.resolve(MOD_ID).resolve("client").resolve(fileName).normalize();
	}

	private static Path getServerWorldDefaultPath(Path configRoot, String fileName) {
		return configRoot.resolve(MOD_ID).resolve("server/world/default").resolve(fileName).normalize();
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
