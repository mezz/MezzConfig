package net.mezzdev.config.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigFileUtilTest {
	@Test
	public void identicalContentsNeedNoWritePermission(@TempDir Path tempDir) throws Exception {
		// Setup: an existing UTF-8 file cannot be replaced and has a recognizable modification time.
		Path path = tempDir.resolve("unchanged.ini");
		List<String> lines = List.of("[general]", "text = café ☃");
		String contents = String.join(System.lineSeparator(), lines) + System.lineSeparator();
		Files.writeString(path, contents);
		Files.setLastModifiedTime(path, FileTime.fromMillis(1_600_000_000_000L));
		FileTime originalTime = Files.getLastModifiedTime(path);
		try (var protection = ConfigFileWriteProtection.protect(path)) {
			// Operation: save identical bytes without needing to create a temporary file or replace the original.
			String fingerprint = ConfigFileUtil.writeUsingTempFileAndGetFingerprint(path, lines);

			// Assertions: contents, metadata, and the fingerprint used by the watcher remain consistent.
			assertEquals(contents, Files.readString(path));
			assertEquals(originalTime, Files.getLastModifiedTime(path));
			assertEquals(ConfigFileReader.read(path).fingerprint(), fingerprint);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void changedBytesAreReplacedIncludingTrailingDifferences(boolean reuseLoadedFingerprint, @TempDir Path tempDir) throws Exception {
		Path path = tempDir.resolve("changed.ini");
		String separator = System.lineSeparator();
		for (String previous : List.of("old" + separator, "new" + separator + "extra" + separator, "new")) {
			// Setup: the file differs in an equal-length value, extra trailing data, or its final newline.
			Files.writeString(path, previous);
			String loadedFingerprint = null;
			if (reuseLoadedFingerprint) {
				loadedFingerprint = ConfigFileReader.read(path).fingerprint();
			}

			// Operation: save the desired complete contents.
			String fingerprint = ConfigFileUtil.writeUsingTempFileAndGetFingerprint(path, List.of("new"), loadedFingerprint);

			// Assertions: the byte comparison does not mistake any of the old contents for an unchanged file.
			assertEquals("new" + separator, Files.readString(path));
			assertEquals(ConfigFileReader.read(path).fingerprint(), fingerprint);
		}
	}

	@Test
	public void writeAcceptsTheMaximumReadableByteCount(@TempDir Path tempDir) throws Exception {
		// Setup: one line plus the platform line separator exactly reaches the readable byte limit.
		Path path = tempDir.resolve("maximum.ini");
		int lineSeparatorBytes = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
		String maximumLine = "x".repeat(ConfigFileReader.MAX_FILE_BYTES - lineSeparatorBytes);

		// Operation: write the boundary-sized config file atomically.
		ConfigFileUtil.writeUsingTempFile(path, List.of(maximumLine));

		// Assertions: a file at the exact limit remains readable without changing its contents.
		assertEquals(ConfigFileReader.MAX_FILE_BYTES, Files.size(path));
		assertEquals(List.of(maximumLine), ConfigFileReader.read(path).lines());
	}

	@Test
	public void oversizedWriteIsRejectedBeforeReplacingTheFile(@TempDir Path tempDir) throws IOException {
		// Setup: an existing file would be replaced by content that exceeds the readable byte limit.
		Path path = tempDir.resolve("oversized.ini");
		Files.writeString(path, "original");
		String oversizedLine = "x".repeat(ConfigFileReader.MAX_FILE_BYTES);

		// Operation: attempt the oversized atomic write.
		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, List.of(oversizedLine))
		);

		// Assertions: validation fails before the original file is replaced.
		assertEquals("original", Files.readString(path));
	}

	@Test
	public void writeAcceptsTheMaximumReadableLineCount(@TempDir Path tempDir) throws Exception {
		// Setup: one file has the maximum readable line count.
		Path maximumPath = tempDir.resolve("maximum-lines.ini");
		List<String> maximumLines = Collections.nCopies(ConfigFileReader.MAX_FILE_LINES, "");

		// Operation: write the file at the line-count boundary.
		ConfigFileUtil.writeUsingTempFile(maximumPath, maximumLines);

		// Assertions: the boundary line count remains readable.
		assertEquals(ConfigFileReader.MAX_FILE_LINES, ConfigFileReader.read(maximumPath).lines().size());
	}

	@Test
	public void excessiveLineCountIsRejectedBeforeReplacingTheFile(@TempDir Path tempDir) throws IOException {
		// Setup: an existing file would be replaced by one line beyond the readable limit.
		Path path = tempDir.resolve("too-many-lines.ini");
		Files.writeString(path, "original");
		List<String> lines = Collections.nCopies(ConfigFileReader.MAX_FILE_LINES + 1, "");

		// Operation: attempt the excessive-line-count write.
		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, lines)
		);

		// Assertions: validation fails before the original file is replaced.
		assertEquals("original", Files.readString(path));
	}

	@Test
	public void embeddedLineSeparatorsCountTowardTheReadableLimit(@TempDir Path tempDir) throws IOException {
		// Setup: one supplied string contains enough embedded separators to exceed the line limit.
		Path path = tempDir.resolve("embedded-lines.ini");
		Files.writeString(path, "original");
		String line = "\n".repeat(ConfigFileReader.MAX_FILE_LINES);

		// Operation: attempt to write the logically multi-line string.
		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, List.of(line))
		);

		// Assertions: embedded separators are rejected before the original file is replaced.
		assertEquals("original", Files.readString(path));
	}
}
