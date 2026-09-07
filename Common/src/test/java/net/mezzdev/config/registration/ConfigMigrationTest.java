package net.mezzdev.config.registration;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.migration.ConfigMigrationStatus;
import net.mezzdev.config.api.migration.IConfigMigrationContext;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import net.mezzdev.config.api.migration.IConfigMigrator;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.migration.ISortingConfigMigrationContext;
import net.mezzdev.config.api.migration.ISortingConfigMigrator;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.server.ServerConfigKey;
import net.mezzdev.config.server.ServerConfigValueData;
import net.mezzdev.config.serializers.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigMigrationTest {
	private static final String MOD_ID = "migration_test";

	@ParameterizedTest
	@EnumSource(value = ConfigSchemaType.class, names = {"CLIENT_PER_WORLD", "SERVER"})
	public void migrationWaitsForFirstLocalWorldAndCompletesOnce(ConfigSchemaType type, @TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("legacy.cfg");
		Files.writeString(legacyPath, "42");
		Path firstPath = configRoot.resolve("worlds/first.ini");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		ConfigSchemaBuilder builder = createWorldBuilder(type, configRoot, activePath);
		IConfigValue<Integer> count = builder.addCategory("general").addInteger("count", 1).build();
		AtomicInteger migrations = new AtomicInteger();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> {
			migrations.incrementAndGet();
			context.set(count, Integer.parseInt(Files.readString(path)));
		});
		builder.setLegacyMigration(List.of(legacyPath), migrator);
		ConfigSchema schema = builder.build();

		assertEquals(1, count.get());
		assertEquals(0, migrator.completionCount);
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
		if (type == ConfigSchemaType.SERVER) {
			schema.applyRemoteSnapshot(List.of(new ServerConfigValueData("general", "count", "7")));
			assertEquals(7, count.get());
			schema.clearRemoteSnapshot();
			assertEquals(0, migrator.completionCount);
		}

		activePath.set(Optional.of(firstPath));
		schema.loadIfNeeded();
		assertEquals(42, count.get());
		assertEquals(ConfigMigrationStatus.MIGRATED, migrator.getResult().getStatus());
		assertEquals(firstPath, migrator.getResult().getDestinationPath().orElseThrow());
		assertTrue(Files.isRegularFile(firstPath));
		assertEquals("42", Files.readString(ConfigFileUtil.getBackupPath(legacyPath, 1)));

		activePath.set(Optional.empty());
		schema.loadIfNeeded();
		activePath.set(Optional.of(configRoot.resolve("worlds/second.ini")));
		schema.loadIfNeeded();
		assertEquals(1, count.get());
		assertEquals(1, migrations.get());
		assertEquals(1, migrator.completionCount);
	}

	@ParameterizedTest
	@EnumSource(value = ConfigSchemaType.class, names = {"CLIENT_PER_WORLD", "SERVER"})
	public void alternateSourcesWaitForWorldActivation(ConfigSchemaType type, @TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("legacy.ini");
		Files.writeString(legacyPath, "[general]\noldCount = 42\n");
		AtomicReference<Optional<Path>> activePath = new AtomicReference<>(Optional.empty());
		ConfigSchemaBuilder builder = createWorldBuilder(type, configRoot, activePath);
		IConfigValue<Integer> count = builder.addCategory("general").addInteger("count", 1)
			.addLegacyName("oldCount").build();
		builder.setLegacySources(List.of(legacyPath));
		ConfigSchema schema = builder.build();
		assertEquals(1, count.get());
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));

		Path destinationPath = configRoot.resolve("worlds/first.ini");
		activePath.set(Optional.of(destinationPath));
		schema.loadIfNeeded();

		assertEquals(42, count.get());
		assertTrue(Files.readString(destinationPath).contains("count = 42"));
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	@Test
	public void permanentlyInactiveDeclarationCompletesWithoutReadingLegacyFile(@TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("legacy.cfg");
		Files.writeString(legacyPath, "42");
		ConfigFileWatcherSettings disabled = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigSchemaBuilder builder = new ConfigSchemaBuilder(
			MOD_ID, Optional::empty, MOD_ID, new ConfigManager(MOD_ID, disabled, disabled),
			ConfigSchemaType.CLIENT, null, false
		);
		IConfigValue<Integer> count = builder.addCategory("general").addInteger("count", 1).build();
		AtomicBoolean migrated = new AtomicBoolean();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> migrated.set(true));
		builder.setLegacyMigration(List.of(legacyPath), migrator);
		ConfigSchema schema = builder.build();
		schema.loadIfNeeded();

		assertEquals(1, count.get());
		assertFalse(migrated.get());
		assertEquals(ConfigMigrationStatus.SKIPPED_INACTIVE, migrator.getResult().getStatus());
		assertEquals(1, migrator.completionCount);
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	private static ConfigSchemaBuilder createWorldBuilder(
		ConfigSchemaType type,
		Path configRoot,
		AtomicReference<Optional<Path>> activePath
	) {
		ConfigFileWatcherSettings disabled = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ServerConfigKey serverKey = null;
		if (type == ConfigSchemaType.SERVER) {
			serverKey = new ServerConfigKey(MOD_ID, "server.ini");
		}
		return new ConfigSchemaBuilder(
			MOD_ID, new LayeredConfigSchemaPathResolver(configRoot.resolve("default/schema.ini"), activePath::get),
			MOD_ID, new ConfigManager(MOD_ID, disabled, disabled), type,
			serverKey
		);
	}

	@Test
	public void migratesFirstExistingLegacyFileAsOneTypedTransaction(@TempDir Path configRoot) throws IOException {
		Path missingLegacyPath = configRoot.resolve("old/missing.cfg");
		Path selectedLegacyPath = configRoot.resolve("old/selected.cfg");
		Path laterLegacyPath = configRoot.resolve("old/later.cfg");
		String legacyContents = "false\nthird\nfirst\nsecond\n";
		writeFile(selectedLegacyPath, legacyContents);
		writeFile(laterLegacyPath, "true\nsecond\nfirst\n");
		AtomicBoolean backupExistedDuringCallback = new AtomicBoolean();

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Boolean> enabled = builder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		RecordingMigrator migrator = new RecordingMigrator(
			(path, context) -> {
				backupExistedDuringCallback.set(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
				List<String> lines = Files.readAllLines(path);
				List<String> sortedValues = lines.subList(1, lines.size());
				context.set(enabled, Boolean.parseBoolean(lines.getFirst()));
				context.setSortedValues(sortingConfig, sortedValues, sortedValues);
			}
		);
		builder.setLegacyMigration(List.of(missingLegacyPath, selectedLegacyPath, laterLegacyPath), migrator);

		builder.build();

		assertFalse(enabled.get());
		assertTrue(backupExistedDuringCallback.get());
		assertEquals(
			List.of("third", "first", "second", "fourth"),
			sortingConfig.getSortedValues(List.of("first", "second", "third", "fourth"))
		);
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.MIGRATED, result.getStatus());
		assertEquals(getClientPath(configRoot, "client.ini"), result.getDestinationPath().orElseThrow());
		assertEquals(selectedLegacyPath, result.getLegacyPath().orElseThrow());
		assertEquals(ConfigFileUtil.getBackupPath(selectedLegacyPath, 1), result.getBackupPath().orElseThrow());
		assertTrue(result.getFailure().isEmpty());
		assertEquals(legacyContents, Files.readString(selectedLegacyPath));
		assertEquals(legacyContents, Files.readString(result.getBackupPath().orElseThrow()));
		assertTrue(Files.isRegularFile(getClientPath(configRoot, "client.ini")));
		assertTrue(Files.isRegularFile(getClientPath(configRoot, "sorting.ini")));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(laterLegacyPath, 1)));
	}

	@Test
	public void alternateSourcesUseDeclaredValueMappings(@TempDir Path configRoot) throws IOException {
		Path missingLegacyPath = configRoot.resolve("old/missing.ini");
		Path selectedLegacyPath = configRoot.resolve("old/selected.ini");
		Path laterLegacyPath = configRoot.resolve("old/later.ini");
		String legacyContents = """
			[general]
			oldEnabled = false
			name = "imported"

			[oldCategory]
			countText = "7"
			""";
		writeFile(selectedLegacyPath, legacyContents);
		writeFile(laterLegacyPath, "[general]\noldEnabled = true\n");

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		var general = builder.addCategory("general");
		IConfigValue<Boolean> enabled = general.addBoolean("enabled", true)
			.addLegacyName("oldEnabled")
			.build();
		IConfigValue<String> name = general.addString("name", "default")
			.build();
		IConfigValue<Integer> count = general.addInteger("count", 0)
			.addLegacyValueMigration("oldCategory", "countText", StringSerializer.INSTANCE, Integer::parseInt)
			.build();
		builder.setLegacySources(List.of(missingLegacyPath, selectedLegacyPath, laterLegacyPath));

		builder.build();

		assertFalse(enabled.get());
		assertEquals("imported", name.get());
		assertEquals(7, count.get());
		Path destinationPath = getClientPath(configRoot, "client.ini");
		assertTrue(Files.isRegularFile(destinationPath));
		assertEquals(legacyContents, Files.readString(selectedLegacyPath));
		assertEquals(legacyContents, Files.readString(ConfigFileUtil.getBackupPath(selectedLegacyPath, 1)));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(laterLegacyPath, 1)));
		List<String> destinationLines = Files.readAllLines(destinationPath);
		assertTrue(destinationLines.stream().anyMatch(line -> line.strip().equals("enabled = false")));
		assertTrue(destinationLines.stream().anyMatch(line -> line.strip().equals("count = 7")));
		assertFalse(destinationLines.stream().anyMatch(line -> line.contains("oldEnabled") || line.contains("oldCategory")));
	}

	@Test
	public void malformedAlternateSourcePreservesDefaultsAndDoesNotCreateDestination(@TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("old/client.ini");
		Files.createDirectories(legacyPath.getParent());
		byte[] malformedContents = {(byte) 0xC3, (byte) 0x28};
		Files.write(legacyPath, malformedContents);

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Boolean> enabled = builder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		builder.setLegacySources(List.of(legacyPath));

		builder.build();

		assertTrue(enabled.get());
		assertFalse(Files.exists(getClientPath(configRoot, "client.ini")));
		assertEquals(-1L, Files.mismatch(legacyPath, ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	@Test
	public void schemaAllowsOnlyOneLegacySourceOrMigrationRegistration(@TempDir Path configRoot) {
		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		builder.setLegacySources(List.of(configRoot.resolve("old/client.ini")));

		assertThrows(
			IllegalStateException.class,
			() -> builder.setLegacyMigration(List.of(configRoot.resolve("older/client.ini")), (path, context) -> {})
		);
	}

	@Test
	public void standaloneSortingMigrationRunsBeforeTheSavedOrderIsLoaded(@TempDir Path configRoot) throws IOException {
		Path missingLegacyPath = configRoot.resolve("old/missing-order.txt");
		Path selectedLegacyPath = configRoot.resolve("old/selected-order.txt");
		Path laterLegacyPath = configRoot.resolve("old/later-order.txt");
		String legacyContents = "third\nfirst\nsecond\n";
		writeFile(selectedLegacyPath, legacyContents);
		writeFile(laterLegacyPath, "second\nfirst\n");

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		AtomicBoolean backupExistedDuringCallback = new AtomicBoolean();
		RecordingSortingMigrator<String> migrator = new RecordingSortingMigrator<>((path, context) -> {
			backupExistedDuringCallback.set(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
			List<String> legacyValues = Files.readAllLines(path);
			context.setSortedValues(legacyValues, legacyValues);
		});
		sortingConfig.setLegacyMigration(
			List.of(missingLegacyPath, selectedLegacyPath, laterLegacyPath),
			migrator
		);
		Path destinationPath = getClientPath(configRoot, "sorting.ini");
		assertFalse(Files.exists(destinationPath));

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("first", "second", "third", "fourth"));

		assertEquals(List.of("third", "first", "second", "fourth"), sortedValues);
		assertTrue(backupExistedDuringCallback.get());
		assertTrue(Files.isRegularFile(destinationPath));
		assertEquals(legacyContents, Files.readString(selectedLegacyPath));
		assertEquals(legacyContents, Files.readString(ConfigFileUtil.getBackupPath(selectedLegacyPath, 1)));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(laterLegacyPath, 1)));
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.MIGRATED, result.getStatus());
		assertEquals(destinationPath, result.getDestinationPath().orElseThrow());
		assertEquals(selectedLegacyPath, result.getLegacyPath().orElseThrow());
		assertEquals(ConfigFileUtil.getBackupPath(selectedLegacyPath, 1), result.getBackupPath().orElseThrow());
	}

	@Test
	public void existingSortingDestinationSkipsStandaloneMigration(@TempDir Path configRoot) throws IOException {
		Path destinationPath = getClientPath(configRoot, "sorting.ini");
		Path legacyPath = configRoot.resolve("old/sorting.txt");
		writeFile(destinationPath, "[visible]\n\\=second\n\\=first\n[hidden]\n");
		writeFile(legacyPath, "first\nsecond\n");
		AtomicBoolean called = new AtomicBoolean();

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		RecordingSortingMigrator<String> migrator = new RecordingSortingMigrator<>((path, context) -> called.set(true));
		sortingConfig.setLegacyMigration(List.of(legacyPath), migrator);

		assertEquals(List.of("second", "first"), sortingConfig.getSortedValues(List.of("first", "second")));

		assertFalse(called.get());
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.SKIPPED_DESTINATION_EXISTS, result.getStatus());
		assertEquals(destinationPath, result.getDestinationPath().orElseThrow());
		assertTrue(result.getLegacyPath().isEmpty());
	}

	@Test
	public void failedStandaloneSortingMigrationDoesNotCreateAPartialDestination(@TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("old/sorting.txt");
		String legacyContents = "second\nfirst\n";
		writeFile(legacyPath, legacyContents);

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		RecordingSortingMigrator<String> migrator = new RecordingSortingMigrator<>((path, context) -> {
			context.setSortedValues(List.of("first", "second"), List.of("second", "first"));
			throw new IOException("legacy sorting parser failed");
		});
		sortingConfig.setLegacyMigration(List.of(legacyPath), migrator);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));

		assertFalse(Files.exists(getClientPath(configRoot, "sorting.ini")));
		assertEquals(legacyContents, Files.readString(legacyPath));
		assertEquals(legacyContents, Files.readString(ConfigFileUtil.getBackupPath(legacyPath, 1)));
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.FAILED, result.getStatus());
		assertEquals("legacy sorting parser failed", result.getFailure().orElseThrow().getMessage());
	}

	@Test
	public void sortingMigrationMustBeRegisteredBeforeTheSavedOrderLoads(@TempDir Path configRoot) {
		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		sortingConfig.getSortedValues(List.of("first"));

		assertThrows(
			IllegalStateException.class,
			() -> sortingConfig.setLegacyMigration(
				List.of(configRoot.resolve("old/sorting.txt")),
				(path, context) -> {}
			)
		);
	}

	@Test
	public void callbackFailurePreservesAllExistingAndQueuedState(@TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("old/client.cfg");
		String legacyContents = "legacy input";
		writeFile(legacyPath, legacyContents);

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		ISortingConfig<String> sortingConfig = registration.createSortingConfig(
			"sorting.ini",
			Comparator.naturalOrder(),
			true
		);
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first")));
		Path sortingPath = getClientPath(configRoot, "sorting.ini");
		String sortingContents = Files.readString(sortingPath);

		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Boolean> enabled = builder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> {
			context.set(enabled, false);
			context.setSortedValues(
				sortingConfig,
				List.of("first", "second"),
				List.of("first", "second")
			);
			throw new IOException("legacy parser failed");
		});
		builder.setLegacyMigration(List.of(legacyPath), migrator);

		builder.build();

		assertTrue(enabled.get());
		assertFalse(Files.exists(getClientPath(configRoot, "client.ini")));
		assertEquals(sortingContents, Files.readString(sortingPath));
		assertEquals(
			List.of("second", "first"),
			sortingConfig.getSortedValues(List.of("first", "second"))
		);
		assertEquals(legacyContents, Files.readString(legacyPath));
		assertEquals(legacyContents, Files.readString(ConfigFileUtil.getBackupPath(legacyPath, 1)));
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.FAILED, result.getStatus());
		assertEquals("legacy parser failed", result.getFailure().orElseThrow().getMessage());
		assertEquals(legacyPath, result.getLegacyPath().orElseThrow());
		assertEquals(ConfigFileUtil.getBackupPath(legacyPath, 1), result.getBackupPath().orElseThrow());
	}

	@Test
	public void invalidTypedUpdateFailsBeforeCreatingTheDestination(@TempDir Path configRoot) throws IOException {
		Path legacyPath = configRoot.resolve("old/client.cfg");
		writeFile(legacyPath, "invalid migrated value");

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Integer> bounded = builder.addCategory("general")
			.addInteger("bounded", 5, 0, 10)
			.build();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> context.set(bounded, 11));
		builder.setLegacyMigration(List.of(legacyPath), migrator);

		builder.build();

		assertEquals(5, bounded.get());
		assertFalse(Files.exists(getClientPath(configRoot, "client.ini")));
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.FAILED, result.getStatus());
		assertTrue(result.getFailure().orElseThrow() instanceof IllegalArgumentException);
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	@Test
	public void existingDestinationSkipsMigrationWithoutInspectingLegacyFiles(@TempDir Path configRoot) throws IOException {
		Path destination = getClientPath(configRoot, "client.ini");
		Path legacyPath = configRoot.resolve("old/client.cfg");
		writeEnabled(destination, false);
		writeFile(legacyPath, "legacy input");
		AtomicBoolean called = new AtomicBoolean();

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Boolean> enabled = builder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> called.set(true));
		builder.setLegacyMigration(List.of(legacyPath), migrator);

		builder.build();

		assertFalse(called.get());
		assertFalse(enabled.get());
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.SKIPPED_DESTINATION_EXISTS, result.getStatus());
		assertEquals(destination, result.getDestinationPath().orElseThrow());
		assertTrue(result.getLegacyPath().isEmpty());
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	@Test
	public void existingDestinationDoesNotRunAlternateSourceValueMigration(@TempDir Path configRoot) throws IOException {
		Path destination = getClientPath(configRoot, "client.ini");
		Path legacyPath = configRoot.resolve("old/client.ini");
		writeEnabled(destination, false);
		writeFile(legacyPath, "[general]\noldEnabled = yes\n");
		AtomicBoolean called = new AtomicBoolean();

		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		IConfigValue<Boolean> enabled = builder.addCategory("general")
			.addBoolean("enabled", true)
			.addLegacyValueMigration(
				"general",
				"oldEnabled",
				StringSerializer.INSTANCE,
				legacyValue -> {
					called.set(true);
					return true;
				}
			)
			.build();
		builder.setLegacySources(List.of(legacyPath));

		builder.build();

		assertFalse(called.get());
		assertFalse(enabled.get());
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
	}

	@Test
	public void missingLegacyFilesReturnSkippedResultAndCreateDefaults(@TempDir Path configRoot) {
		Path missingLegacyPath = configRoot.resolve("old/missing.cfg");
		IConfigRegistration registration = ConfigProvider.createRegistration(configRoot, MOD_ID);
		IConfigSchemaBuilder builder = registration.createClientSchemaBuilder("client.ini", "migration_test.client");
		builder.addCategory("general")
			.addBoolean("enabled", true)
			.build();
		RecordingMigrator migrator = new RecordingMigrator((path, context) -> {});
		builder.setLegacyMigration(List.of(missingLegacyPath), migrator);

		builder.build();

		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.SKIPPED_NO_LEGACY_FILE, result.getStatus());
		assertTrue(result.getLegacyPath().isEmpty());
		assertTrue(Files.isRegularFile(getClientDefaultPath(configRoot, "client.ini")));
		assertFalse(Files.exists(getClientPath(configRoot, "client.ini")));
	}

	private static Path getClientPath(Path configRoot, String fileName) {
		return configRoot.resolve(MOD_ID).resolve("client").resolve(fileName).toAbsolutePath().normalize();
	}

	private static Path getClientDefaultPath(Path configRoot, String fileName) {
		return configRoot.resolve(MOD_ID).resolve("client/default").resolve(fileName).toAbsolutePath().normalize();
	}

	private static void writeEnabled(Path path, boolean enabled) throws IOException {
		writeFile(path, "[general]\nenabled = " + enabled + "\n");
	}

	private static void writeFile(Path path, String contents) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, contents);
	}

	private static final class RecordingMigrator implements IConfigMigrator {
		private final IConfigMigrator delegate;
		private IConfigMigrationResult result;
		private int completionCount;

		private RecordingMigrator(IConfigMigrator delegate) {
			this.delegate = delegate;
		}

		@Override
		public void migrate(Path legacyPath, IConfigMigrationContext context) throws Exception {
			delegate.migrate(legacyPath, context);
		}

		@Override
		public void onMigrationComplete(IConfigMigrationResult result) {
			this.result = result;
			completionCount++;
		}

		private IConfigMigrationResult getResult() {
			if (result == null) {
				throw new IllegalStateException("Migration has not completed.");
			}
			return result;
		}
	}

	private static final class RecordingSortingMigrator<T> implements ISortingConfigMigrator<T> {
		private final ISortingConfigMigrator<T> delegate;
		private IConfigMigrationResult result;

		private RecordingSortingMigrator(ISortingConfigMigrator<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void migrate(Path legacyPath, ISortingConfigMigrationContext<T> context) throws Exception {
			delegate.migrate(legacyPath, context);
		}

		@Override
		public void onMigrationComplete(IConfigMigrationResult result) {
			this.result = result;
		}

		private IConfigMigrationResult getResult() {
			if (result == null) {
				throw new IllegalStateException("Migration has not completed.");
			}
			return result;
		}
	}
}
