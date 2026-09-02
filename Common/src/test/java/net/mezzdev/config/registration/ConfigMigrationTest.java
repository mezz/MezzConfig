package net.mezzdev.config.registration;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.migration.ConfigMigrationStatus;
import net.mezzdev.config.api.migration.IConfigMigrationContext;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import net.mezzdev.config.api.migration.IConfigMigrator;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.sorting.ISortingConfigMigrationContext;
import net.mezzdev.config.api.sorting.ISortingConfigMigrator;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.serializers.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigMigrationTest {
	private static final String MOD_ID = "migration_test";

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

		assertFalse(enabled.getValue());
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

		assertFalse(enabled.getValue());
		assertEquals("imported", name.getValue());
		assertEquals(7, count.getValue());
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

		assertTrue(enabled.getValue());
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

		assertTrue(enabled.getValue());
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

		assertEquals(5, bounded.getValue());
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
		assertFalse(enabled.getValue());
		IConfigMigrationResult result = migrator.getResult();
		assertEquals(ConfigMigrationStatus.SKIPPED_DESTINATION_EXISTS, result.getStatus());
		assertEquals(destination, result.getDestinationPath().orElseThrow());
		assertTrue(result.getLegacyPath().isEmpty());
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
