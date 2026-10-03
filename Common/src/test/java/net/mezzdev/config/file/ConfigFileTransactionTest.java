package net.mezzdev.config.file;

import com.sun.nio.file.ExtendedOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigFileTransactionTest {
	@Test
	public void successfulCommitReplacesAndCreatesFilesAndRemovesBackups(@TempDir Path tempDir) throws IOException {
		Path existing = Files.writeString(tempDir.resolve("existing.ini"), "original");
		Path created = tempDir.resolve("created.ini");
		var outputs = new LinkedHashMap<Path, List<String>>();
		outputs.put(existing, List.of("replacement"));
		outputs.put(created, List.of("created"));

		ConfigFileTransaction.write(outputs, Set.of(created));

		assertEquals("replacement" + System.lineSeparator(), Files.readString(existing));
		assertEquals("created" + System.lineSeparator(), Files.readString(created));
		assertDirectoryContains(tempDir, existing, created);
	}

	@Test
	public void stagingFailurePreservesTargetsAndRemovesTemporaryFiles(@TempDir Path tempDir) throws IOException {
		Path existing = Files.writeString(tempDir.resolve("existing.ini"), "original");
		Path created = tempDir.resolve("created.ini");
		Path invalid = tempDir.resolve("invalid.ini");
		var outputs = new LinkedHashMap<Path, List<String>>();
		outputs.put(existing, List.of("replacement"));
		outputs.put(created, List.of("created"));
		outputs.put(invalid, Collections.nCopies(ConfigFileReader.MAX_FILE_LINES + 1, ""));

		assertThrows(IllegalArgumentException.class, () -> ConfigFileTransaction.write(outputs, Set.of(created, invalid)));

		assertEquals("original", Files.readString(existing));
		assertFalse(Files.exists(created));
		assertFalse(Files.exists(invalid));
		assertDirectoryContains(tempDir, existing);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void commitFailureRestoresEarlierTargetsAndRemovesCreatedFiles(@TempDir Path tempDir) throws IOException {
		Path existing = Files.writeString(tempDir.resolve("existing.ini"), "original");
		Path created = tempDir.resolve("created.ini");
		Path locked = Files.writeString(tempDir.resolve("locked.ini"), "locked original");
		var outputs = new LinkedHashMap<Path, List<String>>();
		outputs.put(existing, List.of("replacement"));
		outputs.put(created, List.of("created"));
		outputs.put(locked, List.of("blocked replacement"));

		// Allow staging to read the last target, but prevent its replacement after earlier files commit.
		try (var lock = FileChannel.open(locked, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			assertThrows(FileSystemException.class, () -> ConfigFileTransaction.write(outputs, Set.of(created)));
		}

		assertEquals("original", Files.readString(existing));
		assertFalse(Files.exists(created));
		assertEquals("locked original", Files.readString(locked));
		assertDirectoryContains(tempDir, existing, locked);
	}

	private static void assertDirectoryContains(Path directory, Path... expected) throws IOException {
		try (var files = Files.list(directory)) {
			assertEquals(Set.of(expected), files.collect(Collectors.toSet()));
		}
	}
}
