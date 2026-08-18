package net.mezzdev.config.file;

import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.api.value.IConfigValueSerializer;
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

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertFalse(enabled.getValue());
		assertEquals(1, count.getValue());
		assertEquals(malformed, Files.readAllLines(ConfigFileUtil.getBackupPath(path, 1)));
		String corrected = Files.readString(path);
		assertTrue(corrected.contains("enabled = false"));
		assertTrue(corrected.contains("count = 1"));
		assertFalse(corrected.contains("this is not valid"));
		assertFalse(corrected.contains("unknown ="));
	}

	@Test
	public void partialDeserializationKeepsUsableElementsBeforeCorrection(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals(List.of(true, false), flags.getValue());
		assertTrue(Files.readString(path).contains("flags = [\"true\",\"false\"]"));
		assertTrue(Files.readString(ConfigFileUtil.getBackupPath(path, 1)).contains("invalid"));
	}

	@Test
	public void recoveryKeepsOnlyFiveMostRecentBackups(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("test.ini");
		ConfigCategory category = createCategory(createBooleanValue());

		for (int attempt = 1; attempt <= ConfigFileUtil.MAX_BACKUPS + 2; attempt++) {
			Files.write(path, List.of("[general]", "invalid line " + attempt));
			ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));
		}

		for (int index = 1; index <= ConfigFileUtil.MAX_BACKUPS; index++) {
			assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, index)));
		}
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, ConfigFileUtil.MAX_BACKUPS + 1)));
		assertTrue(Files.readString(ConfigFileUtil.getBackupPath(path, 1)).contains("invalid line 7"));
	}

	@Test
	public void unchangedFailedCorrectionIsNotBackedUpOrRewrittenAgain(@TempDir Path tempDir) throws IOException {
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

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));
		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertTrue(Files.readString(path).contains("value = malformed"));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 2)));
	}

	@Test
	public void malformedPlayerOverlayRecoversWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
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

		schema.loadIfNeeded();

		assertTrue(enabled.getValue());
		assertEquals(2, count.getValue());
		assertEquals(packContents, Files.readAllLines(defaultPath));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(playerPath, 1)));
		assertTrue(Files.readString(playerPath).contains("count = 2"));
	}

	@Test
	public void oversizedFileIsRecoveredWithoutUnboundedReading(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("oversized.ini");
		Files.write(path, new byte[ConfigSerializer.MAX_CONFIG_FILE_BYTES + 1]);
		ConfigCategory category = createCategory(createBooleanValue());

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertEquals(ConfigSerializer.MAX_CONFIG_FILE_BYTES + 1, Files.size(ConfigFileUtil.getBackupPath(path, 1)));
		assertTrue(Files.size(path) < ConfigSerializer.MAX_CONFIG_FILE_BYTES);
	}

	@Test
	public void excessiveLineCountIsRecoveredWithoutUnboundedParsing(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("too-many-lines.ini");
		List<String> lines = new ArrayList<>(Collections.nCopies(ConfigSerializer.MAX_CONFIG_FILE_LINES + 1, "# bounded"));
		Files.write(path, lines);
		ConfigCategory category = createCategory(createBooleanValue());

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertTrue(Files.readAllLines(path).size() < ConfigSerializer.MAX_CONFIG_FILE_LINES);
	}

	@Test
	public void invalidUtf8IsBackedUpAndCorrected(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("invalid-utf8.ini");
		byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
		Files.write(path, invalidUtf8);
		ConfigCategory category = createCategory(createBooleanValue());

		ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category));

		assertTrue(Files.readString(path).contains("enabled = true"));
		assertEquals(invalidUtf8.length, Files.size(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void transientReadFailureDoesNotReplaceOrBackUpPath(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("config-directory");
		Files.createDirectory(path);
		ConfigCategory category = createCategory(createBooleanValue());

		assertThrows(
			IOException.class,
			() -> ConfigSerializer.loadWithoutNotifyingUnconditionally(path, List.of(category))
		);

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
