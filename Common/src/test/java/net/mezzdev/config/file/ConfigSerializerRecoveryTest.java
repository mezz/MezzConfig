package net.mezzdev.config.file;

import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.schema.ConfigCategory;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.LayeredConfigSchemaPathResolver;
import net.mezzdev.config.schema.StaticConfigSchemaPathResolver;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.serializers.ListSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSerializerRecoveryTest {
	private static final String LOCALIZATION_PATH = "mezz_config.config.test.general";

	@Test
	public void malformedFileKeepsValidValuesAndAtomicallyRewritesFromFallbacks(@TempDir Path tempDir) throws IOException {
		// Setup: a config file mixes valid, malformed, out-of-range, and unknown entries.
		Path path = tempDir.resolve("test.ini");
		List<String> malformed = List.of(
			"[general]",
			"enabled = false",
			"this is not valid",
			"count = outside-range",
			"unknown = retained-in-backup-only"
		);
		Files.write(path, malformed);
		ConfigValue<Boolean> enabled = createBooleanValue();
		ConfigValue<Integer> count = createIntegerValue();
		ConfigCategory category = createCategory(enabled, count);

		// Operation: load the damaged file and run automatic recovery.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: valid data survives, invalid data falls back, and recovery backs up then corrects the file.
		assertFalse(enabled.get());
		assertEquals(1, count.get());
		assertEquals(malformed, Files.readAllLines(ConfigFileUtil.getBackupPath(path, 1)));
		String corrected = Files.readString(path);
		assertTrue(corrected.contains("enabled = false"));
		assertTrue(corrected.contains("count = 1"));
		assertFalse(corrected.contains("this is not valid"));
		assertFalse(corrected.contains("unknown ="));
	}

	@Test
	public void partialDeserializationKeepsUsableElementsBeforeCorrection(@TempDir Path tempDir) throws IOException {
		// Setup: a stored boolean list contains valid elements around one malformed element.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of(
			"[general]",
			"flags = [\"true\", \"invalid\", \"false\"]"
		));
		ConfigValue<List<Boolean>> flags = new ConfigValue<>(
			LOCALIZATION_PATH,
			"flags",
			List.of(true),
			new ListSerializer<>(BooleanSerializer.INSTANCE)
		);
		ConfigCategory category = createCategory(flags);

		// Operation: load and correct the partially recoverable list.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: usable elements remain, the corrected file omits the bad element, and the backup retains it.
		assertEquals(List.of(true, false), flags.get());
		assertTrue(Files.readString(path).contains("flags = [\"true\",\"false\"]"));
		assertTrue(Files.readString(ConfigFileUtil.getBackupPath(path, 1)).contains("invalid"));
	}

	@Test
	public void recoveryKeepsOnlyFiveMostRecentBackups(@TempDir Path tempDir) throws IOException {
		// Setup: the same config path will fail recovery more times than the backup retention limit.
		Path path = tempDir.resolve("test.ini");
		ConfigCategory category = createCategory(createBooleanValue());

		// Operation: load a newly damaged version on each recovery attempt.
		for (int attempt = 1; attempt <= ConfigFileUtil.MAX_BACKUPS + 2; attempt++) {
			Files.write(path, List.of("[general]", "invalid line " + attempt));
			ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));
		}

		// Assertions: only the newest bounded set remains, with the latest failure at backup index one.
		for (int index = 1; index <= ConfigFileUtil.MAX_BACKUPS; index++) {
			assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, index)));
		}
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, ConfigFileUtil.MAX_BACKUPS + 1)));
		assertTrue(Files.readString(ConfigFileUtil.getBackupPath(path, 1)).contains("invalid line 7"));
	}

	@Test
	public void unchangedFailedCorrectionIsNotBackedUpOrRewrittenAgain(@TempDir Path tempDir) throws IOException {
		// Setup: malformed input needs correction, but its serializer starts failing before the rewrite.
		Path path = tempDir.resolve("test.ini");
		Files.write(path, List.of("[general]", "value = malformed"));
		AtomicBoolean serializationFails = new AtomicBoolean();
		IConfigValueSerializer<String> serializer = new IConfigValueSerializer<>() {
			@Override
			public String serialize(String value) {
				if (serializationFails.get()) {
					throw new IllegalStateException("expected test serialization failure");
				}
				return value;
			}

			@Override
			public IDeserializeResult<String> deserialize(String string) {
				if (string.equals("default")) {
					return IDeserializeResult.success(string);
				}
				return IDeserializeResult.failure("expected malformed value");
			}

			@Override
			public boolean isValid(String value) {
				return true;
			}

			@Override
			public String getValidValuesDescription() {
				return "any string";
			}
		};
		ConfigValue<String> value = new ConfigValue<>(LOCALIZATION_PATH, "value", "default", serializer);
		ConfigCategory category = createCategory(value);
		serializationFails.set(true);

		// Operation: load the same unchanged malformed file twice while correction cannot be serialized.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: the source remains untouched and an identical failed correction creates only one backup.
		assertTrue(Files.readString(path).contains("value = malformed"));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 2)));
	}

	@Test
	public void malformedPlayerOverlayRecoversWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
		// Setup: a valid pack default is overlaid by player data with one valid and one malformed value.
		Path defaultPath = tempDir.resolve("pack.ini");
		Path playerPath = tempDir.resolve("players").resolve("player.ini");
		List<String> packContents = List.of(
			"[general]",
			"enabled = false",
			"count = 2"
		);
		Files.write(defaultPath, packContents);
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of(
			"[general]",
			"enabled = true",
			"count = invalid"
		));
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "general");
		ConfigValue<Boolean> enabled = builder.addBoolean("enabled", true).build();
		ConfigValue<Integer> count = builder.addInteger("count", 1, 0, 10).build();
		ConfigSchema schema = new ConfigSchema(
			new LayeredConfigSchemaPathResolver(defaultPath, new StaticConfigSchemaPathResolver(playerPath)),
			List.of(builder),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);

		// Operation: load the layered schema and recover the player overlay.
		schema.loadIfNeeded();

		// Assertions: player data takes precedence where valid, fallback comes from the pack, and only the overlay changes.
		assertTrue(enabled.get());
		assertEquals(2, count.get());
		assertEquals(packContents, Files.readAllLines(defaultPath));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(playerPath, 1)));
		assertTrue(Files.readString(playerPath).contains("count = 2"));
	}

	@Test
	public void oversizedFileIsRecoveredWithoutUnboundedReading(@TempDir Path tempDir) throws IOException {
		// Setup: a config file is one byte larger than the safe read limit.
		Path path = tempDir.resolve("oversized.ini");
		Files.write(path, new byte[ConfigSerializer.MAX_CONFIG_FILE_BYTES + 1]);
		ConfigCategory category = createCategory(createBooleanValue());

		// Operation: load the oversized file through normal recovery.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: the complete source is backed up and replaced by a bounded default file.
		assertEquals(ConfigSerializer.MAX_CONFIG_FILE_BYTES + 1, Files.size(ConfigFileUtil.getBackupPath(path, 1)));
		assertTrue(Files.size(path) < ConfigSerializer.MAX_CONFIG_FILE_BYTES);
	}

	@Test
	public void excessiveLineCountIsRecoveredWithoutUnboundedParsing(@TempDir Path tempDir) throws IOException {
		// Setup: a config file exceeds the safe line-count limit.
		Path path = tempDir.resolve("too-many-lines.ini");
		List<String> lines = new ArrayList<>(Collections.nCopies(ConfigSerializer.MAX_CONFIG_FILE_LINES + 1, "# bounded"));
		Files.write(path, lines);
		ConfigCategory category = createCategory(createBooleanValue());

		// Operation: load the excessive file through normal recovery.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: recovery preserves a backup and writes a bounded replacement.
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertTrue(Files.readAllLines(path).size() < ConfigSerializer.MAX_CONFIG_FILE_LINES);
	}

	@Test
	public void invalidUtf8IsBackedUpAndCorrected(@TempDir Path tempDir) throws IOException {
		// Setup: a config file contains an invalid UTF-8 byte sequence.
		Path path = tempDir.resolve("invalid-utf8.ini");
		byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
		Files.write(path, invalidUtf8);
		ConfigCategory category = createCategory(createBooleanValue());

		// Operation: load the undecodable file through normal recovery.
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		// Assertions: defaults replace the invalid source after its original bytes are backed up.
		assertTrue(Files.readString(path).contains("enabled = true"));
		assertEquals(invalidUtf8.length, Files.size(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void transientReadFailureDoesNotReplaceOrBackUpPath(@TempDir Path tempDir) throws IOException {
		// Setup: the configured path is temporarily a directory instead of a readable file.
		Path path = tempDir.resolve("config-directory");
		Files.createDirectory(path);
		ConfigCategory category = createCategory(createBooleanValue());

		// Operation: attempt to load the unreadable path.
		assertThrows(
			IOException.class,
			() -> ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category))
		);

		// Assertions: transient I/O failure leaves the path intact and creates no recovery backup.
		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	private static ConfigValue<Boolean> createBooleanValue() {
		return new ConfigValue<>(LOCALIZATION_PATH, "enabled", true, BooleanSerializer.INSTANCE);
	}

	private static ConfigValue<Integer> createIntegerValue() {
		return new ConfigValue<>(LOCALIZATION_PATH, "count", 1, new IntegerSerializer(0, 10));
	}

	private static ConfigCategory createCategory(ConfigValue<?>... values) {
		return new ConfigCategory(LOCALIZATION_PATH, "general", List.of(values));
	}
}
