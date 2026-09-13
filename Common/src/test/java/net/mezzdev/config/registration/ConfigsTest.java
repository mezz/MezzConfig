package net.mezzdev.config.registration;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
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
	public void mezzConfigSettingsSchemaIsDiscoverable() {
		// Operation: locate MezzConfig's own settings schema through the public registry.
		IConfigSchema settingsSchema = Configs.getSchemas().stream()
			.filter(schema -> schema.getModId().equals("mezz_config"))
			.findFirst()
			.orElseThrow();

		// Assertions: the built-in settings schema is an active installation-wide client schema.
		assertEquals("settings.ini", settingsSchema.getId());
		assertEquals(ConfigSchemaType.CLIENT, settingsSchema.getType());
		assertTrue(settingsSchema.isActive());
	}

	@Test
	public void factoriesCreateCompleteClientAndServerSchemaTypes(@TempDir Path configRoot) throws IOException {
		// Setup: conventional and explicit client files exist before all supported schema factories are used.
		IConfigRegistration registration = createRegistration(configRoot);
		Path clientPath = getClientPath(configRoot, "client.ini");
		Path explicitPath = configRoot.resolve("outside-owned-layout/explicit.ini");
		writeEnabled(clientPath, false);
		writeEnabled(explicitPath, true);

		// Operation: create conventional client, explicit client, and contextual server schemas.
		TestSchema client = createSchema(
			registration.createClientSchemaBuilder("client.ini", "registration_test.client"),
			true
		);
		TestSchema explicit = createSchema(
			registration.createClientSchemaBuilder(explicitPath, "registration_test.explicit"),
			false
		);
		TestSchema server = createSchema(
			registration.createServerSchemaBuilder("server.ini", "registration_test.server"),
			true
		);

		// Assertions: each factory assigns the correct identity, ownership, path, activation, and loaded value.
		assertEquals(ConfigSchemaType.CLIENT, client.schema().getType());
		assertEquals(ConfigSchemaType.CLIENT, explicit.schema().getType());
		assertEquals(ConfigSchemaType.SERVER, server.schema().getType());
		assertEquals("client.ini", client.schema().getId());
		assertEquals(explicitPath.toString().replace('\\', '/'), explicit.schema().getId());
		assertEquals("server.ini", server.schema().getId());
		assertTrue(client.schema().isActive());
		assertFalse(client.enabled().get());
		assertTrue(explicit.schema().isActive());
		assertTrue(explicit.enabled().get());
		assertEquals(clientPath, client.schema().getPath().orElseThrow());
		assertEquals(explicitPath, explicit.schema().getPath().orElseThrow());
		assertFalse(server.schema().isActive());
		assertEquals(Optional.empty(), server.schema().getPath());
		assertTrue(getConfigManager().getServerSchemas().contains(server.schema()));
	}

	@Test
	public void gameRestartRequirementKeepsSavedValuePending(@TempDir Path configRoot) throws IOException {
		// Setup: a game-restart value loads false from an existing client config file.
		Path path = getClientPath(configRoot, FILE_NAME);
		writeEnabled(path, false);
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(
			registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"),
			true,
			ConfigValueRestartRequirement.GAME_RESTART
		);

		// Operation: select and save true without restarting the game.
		assertTrue(config.enabled().set(true));

		// Assertions: the effective value stays false while the pending selection is persisted.
		assertFalse(config.enabled().get());
		assertTrue(config.enabled().getEditorInfo().getPendingValue());
		awaitFileContent(path, "enabled = true");
	}

	@Test
	public void externalChangesReloadClientValues(@TempDir Path configRoot) throws IOException {
		// Setup: an actively watched client schema initially loads true from disk.
		Path path = getClientPath(configRoot, FILE_NAME);
		writeEnabled(path, true);
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), false);

		// Operation: replace the file with false and wait for the watcher reload.
		writeEnabled(path, false);

		awaitValue(config.enabled(), false);

		// Assertions: both effective and editor-visible pending state reflect the external change.
		assertFalse(config.enabled().getEditorInfo().getPendingValue());
	}

	@Test
	public void missingClientPackDefaultsAreCreatedSynchronously(@TempDir Path configRoot) {
		// Setup: no pack default or user override exists for a new client schema.
		IConfigRegistration registration = createRegistration(configRoot);

		// Operation: build the schema with its declared default value.
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		// Assertions: registration writes the pack default synchronously without creating a user override.
		assertEquals(ConfigValueRestartRequirement.NONE, config.enabled().getEditorInfo().getRestartRequirement());
		assertTrue(Files.exists(getClientDefaultPath(configRoot, FILE_NAME)));
		assertFalse(Files.exists(getClientPath(configRoot, FILE_NAME)));
	}

	@Test
	public void clientPackDefaultsAreOverriddenOnlyAfterTheUserChangesAValue(@TempDir Path configRoot) throws IOException {
		// Setup: a client schema loads false from a pack default and has no user override.
		Path defaultPath = getClientDefaultPath(configRoot, FILE_NAME);
		Path userPath = getClientPath(configRoot, FILE_NAME);
		writeEnabled(defaultPath, false);
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		assertFalse(config.enabled().get());
		assertFalse(Files.exists(userPath));

		// Operation: change the value from the pack-provided default.
		assertTrue(config.enabled().set(true));

		// Assertions: the user override is saved while the pack default remains unchanged.
		awaitFileContent(userPath, "enabled = true");
		assertTrue(Files.readString(defaultPath).contains("enabled = false"));
	}

	@Test
	public void serverFactoryCreatesContextualSynchronizedSchema(@TempDir Path configRoot) {
		// Setup: no server world is active when a server-owned schema is declared.
		IConfigRegistration registration = createRegistration(configRoot);

		// Operation: build the schema through the server factory.
		TestSchema config = createSchema(
			registration.createServerSchemaBuilder(FILE_NAME, "registration_test.server"),
			true
		);

		// Assertions: it is registered for synchronization but remains pathless and does not create world data yet.
		assertEquals(ConfigSchemaType.SERVER, config.schema().getType());
		assertFalse(config.schema().isActive());
		assertTrue(getConfigManager().getServerSchemas().contains(config.schema()));
		assertFalse(Files.exists(getServerWorldDefaultPath(configRoot, FILE_NAME)));
	}

	@Test
	public void duplicateClientIdentityIsRejected(@TempDir Path configRoot) {
		// Setup: a client schema already owns its automatic file identity.
		IConfigRegistration registration = createRegistration(configRoot);
		TestSchema original = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);

		// Operation: try to create a duplicate schema through the public factory.
		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);

		// Assertions: the original schema remains published.
		assertTrue(Configs.getSchemas().contains(original.schema()));
	}

	@Test
	public void clientAndClientWorldSchemasCannotShareDefaultPath(@TempDir Path configRoot) throws IOException {
		// Setup: an installation-wide schema already owns the conventional path used by world defaults.
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, "world/default/" + FILE_NAME);
		writeEnabled(path, true);
		createSchema(
			registration.createClientSchemaBuilder("world/default/" + FILE_NAME, "registration_test.client"),
			true
		);
		String originalContents = Files.readString(path);

		// Operation: try to create a client-world schema whose default resolves to that same path.
		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientPerWorldSchemaBuilder(FILE_NAME, "registration_test.client_world"),
				false
			)
		);

		// Assertions: collision detection leaves the existing file unchanged.
		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void clientSchemasCannotSharePackDefaultAndUserPath(@TempDir Path configRoot) throws IOException {
		// Setup: one client schema's pack-default path is another automatic name's user path.
		IConfigRegistration registration = createRegistration(configRoot);
		Path packDefaultPath = getClientDefaultPath(configRoot, FILE_NAME);
		createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		String originalContents = Files.readString(packDefaultPath);

		// Operation: try to register the nested schema with that colliding identity.
		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientSchemaBuilder("default/" + FILE_NAME, "registration_test.client_nested"),
				false
			)
		);

		// Assertions: the original default stays unchanged and no nested default is created.
		assertEquals(originalContents, Files.readString(packDefaultPath));
		assertFalse(Files.exists(getClientDefaultPath(configRoot, "default/" + FILE_NAME)));
	}

	@Test
	public void sortingConfigRejectsSchemaCollisionBeforeCreatingFile(@TempDir Path configRoot) {
		// Setup: a sorting config reserves the automatic path requested by a client schema.
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, FILE_NAME);
		registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true);

		// Operation: try to build the colliding schema.
		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true)
		);

		// Assertions: rejection occurs before either owner creates the file.
		assertFalse(Files.exists(path));
	}

	@Test
	public void sortingFactoriesSupportStringAndGenericValues(@TempDir Path configRoot) {
		// Setup: public factories create string and custom-serialized integer sorting configs.
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

		// Operation and assertions: both configs sort, persist custom order, and track visibility by value type.
		assertEquals(List.of("second", "first"), strings.getSortedValues(List.of("first", "second")));
		assertEquals(List.of(1, 2, 3), integers.getSortedValues(List.of(3, 1, 2)));
		assertTrue(integers.setSortedValues(List.of(1, 2, 3), List.of(3, 1)));
		assertEquals(List.of(3, 1, 4), integers.getSortedValues(List.of(1, 2, 3, 4)));
		assertFalse(integers.isVisible(List.of(1, 2, 3, 4), 2));
	}

	@Test
	public void schemaRejectsSortingCollisionWithoutModifyingFile(@TempDir Path configRoot) throws IOException {
		// Setup: an initialized client schema owns a file that already contains data.
		IConfigRegistration registration = createRegistration(configRoot);
		Path path = getClientPath(configRoot, FILE_NAME);
		writeEnabled(path, true);
		createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), true);
		String originalContents = Files.readString(path);

		// Operation: try to create a sorting config with the same automatic file identity.
		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createSortingConfig(FILE_NAME, Comparator.naturalOrder(), true)
		);

		// Assertions: collision rejection leaves the schema file unchanged.
		assertEquals(originalContents, Files.readString(path));
	}

	@Test
	public void automaticAndExplicitLocationsUseTheSamePathIdentity(@TempDir Path tempDir) {
		// Setup: an automatic sorting path and a normalized explicit schema path resolve to the same file.
		String fileName = "path-identity/" + tempDir.getFileName() + ".ini";
		Path conventionalRoot = Path.of("build", "test-config").toAbsolutePath().normalize();
		Path conventionalPath = getClientPath(conventionalRoot, fileName);
		IConfigRegistration registration = Configs.forMod(MOD_ID);
		registration.createSortingConfig(fileName, Comparator.naturalOrder(), true);

		// Operation: try to register the explicit schema at the reserved conventional path.
		assertThrows(
			IllegalArgumentException.class,
			() -> createSchema(
				registration.createClientSchemaBuilder(conventionalPath, "registration_test.client"),
				true
			)
		);

		// Assertions: shared normalized identity catches the collision before file creation.
		assertFalse(Files.exists(conventionalPath));
	}

	@Test
	public void automaticModIdsCannotEscapeTheirOwnedRoot() {
		// Operation and assertions: automatic mod IDs cannot traverse outside their owned root.
		assertThrows(IllegalArgumentException.class, () -> Configs.forMod("../outside"));
	}

	@Test
	public void automaticFileNamesCannotEscapeTheirOwnedRoot(@TempDir Path configRoot) {
		// Setup: an automatic client schema builder owns paths beneath its config root.
		IConfigRegistration registration = createRegistration(configRoot);

		// Operation and assertions: automatic file names cannot traverse outside the owned root.
		assertThrows(
			IllegalArgumentException.class,
			() -> registration.createClientSchemaBuilder("../outside.ini", "registration_test.client")
		);
	}

	@Test
	public void explicitLocationsMayBeOutsideTheAutomaticRoot(@TempDir Path configRoot) {
		// Setup: an explicit path deliberately targets a caller-owned layout outside the automatic root.
		Path explicitPath = configRoot.resolve("outside-owned-layout/settings.ini");
		IConfigRegistration registration = createRegistration(configRoot);

		// Operation: create a schema at the explicit location.
		TestSchema explicit = createSchema(
			registration.createClientSchemaBuilder(explicitPath, "registration_test.explicit"),
			true
		);

		// Assertions: explicit locations remain supported exactly as supplied.
		assertEquals(explicitPath, explicit.schema().getPath().orElseThrow());
	}

	@Test
	public void relativeExplicitLocationIsCapturedAsNormalizedAbsolutePath(@TempDir Path tempDir) {
		// Setup: an explicit relative path resolves to a nested location outside the automatic config root.
		Path absolutePath = tempDir.resolve("nested/settings.ini").toAbsolutePath().normalize();
		Path relativePath = Path.of("").toAbsolutePath().normalize().relativize(absolutePath);
		IConfigRegistration registration = createRegistration(tempDir.resolve("automatic-root"));

		// Operation: build a schema from the relative explicit path.
		TestSchema config = createSchema(
			registration.createClientSchemaBuilder(relativePath, "registration_test.explicit"),
			true
		);

		// Assertions: registration captures normalized absolute identity and creates the expected file.
		assertEquals(absolutePath, config.schema().getPath().orElseThrow());
		assertTrue(Files.isRegularFile(absolutePath));
	}

	@Test
	public void malformedClientFileUsesNormalRecovery(@TempDir Path configRoot) throws IOException {
		// Setup: a client file mixes valid, malformed, and unknown values.
		Path path = getClientPath(configRoot, FILE_NAME);
		writeFile(path, "[general]\nenabled = true\nbounded = not-an-integer\nunknown = true\n");
		IConfigRegistration registration = createRegistration(configRoot);

		// Operation: build the public client schema and load the damaged file.
		TestSchema config = createSchema(registration.createClientSchemaBuilder(FILE_NAME, "registration_test.client"), false);

		// Assertions: valid data loads, invalid data falls back, and recovery backs up then corrects the file.
		assertTrue(config.enabled().get());
		assertEquals(0, config.bounded().get());
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
		String corrected = Files.readString(path);
		assertTrue(corrected.contains("Config for this game installation"));
		assertTrue(corrected.contains("enabled = true"));
		assertTrue(corrected.contains("bounded = 0"));
		assertFalse(corrected.contains("unknown ="));
	}

	@Test
	public void invalidUtf8ExplicitFileUsesNormalRecovery(@TempDir Path configRoot) throws IOException {
		// Setup: an explicit client file contains invalid UTF-8.
		IConfigRegistration registration = createRegistration(configRoot);
		Path invalidPath = configRoot.resolve("explicit/invalid.ini");
		Files.createDirectories(invalidPath.getParent());
		byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
		Files.write(invalidPath, invalidUtf8);

		// Operation: build a schema that loads the damaged explicit file.
		createSchema(registration.createClientSchemaBuilder(invalidPath, "registration_test.invalid"), true);

		// Assertions: the original bytes are backed up and replaced by valid defaults.
		assertEquals(invalidUtf8.length, Files.size(ConfigFileUtil.getBackupPath(invalidPath, 1)));
		assertTrue(Files.readString(invalidPath).contains("enabled = true"));
	}

	@Test
	public void oversizedExplicitFileUsesBoundedRecovery(@TempDir Path configRoot) throws IOException {
		// Setup: an explicit client file exceeds the readable byte limit.
		IConfigRegistration registration = createRegistration(configRoot);
		Path oversizedPath = configRoot.resolve("explicit/oversized.ini");
		Files.createDirectories(oversizedPath.getParent());
		Files.write(oversizedPath, new byte[MAX_CONFIG_FILE_BYTES + 1]);

		// Operation: build a schema that loads the oversized explicit file.
		createSchema(registration.createClientSchemaBuilder(oversizedPath, "registration_test.oversized"), false);

		// Assertions: recovery backs up the oversized source and writes a bounded default file.
		assertEquals(MAX_CONFIG_FILE_BYTES + 1, Files.size(ConfigFileUtil.getBackupPath(oversizedPath, 1)));
		assertTrue(Files.size(oversizedPath) < MAX_CONFIG_FILE_BYTES);
	}

	@Test
	public void synchronousReadFailureDoesNotPublishSchema(@TempDir Path configRoot) throws IOException {
		// Setup: an explicit schema path is a directory, and the current registry size is known.
		Path path = configRoot.resolve("explicit/client.ini");
		Files.createDirectories(path);
		IConfigRegistration registration = createRegistration(configRoot);
		int schemaCount = Configs.getSchemas().size();

		// Operation: try to build a schema that must synchronously read the invalid path.
		assertThrows(
			UncheckedIOException.class,
			() -> createSchema(
				registration.createClientSchemaBuilder(path, "registration_test.client"),
				true
			)
		);

		// Assertions: the failed schema is not published and recovery does not alter or back up the directory.
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

	private static Path getClientDefaultPath(Path configRoot, String fileName) {
		return configRoot.resolve(MOD_ID).resolve("client/default").resolve(fileName).normalize();
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
			if (Files.exists(path) && Files.readString(path).contains(expected)) {
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
			if (expected.equals(value.get())) {
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
