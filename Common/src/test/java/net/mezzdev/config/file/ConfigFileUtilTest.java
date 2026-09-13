package net.mezzdev.config.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigFileUtilTest {
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
