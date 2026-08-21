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
		Path path = tempDir.resolve("maximum.ini");
		int lineSeparatorBytes = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
		String maximumLine = "x".repeat(ConfigFileReader.MAX_FILE_BYTES - lineSeparatorBytes);

		ConfigFileUtil.writeUsingTempFile(path, List.of(maximumLine));

		assertEquals(ConfigFileReader.MAX_FILE_BYTES, Files.size(path));
		assertEquals(List.of(maximumLine), ConfigFileReader.read(path).lines());
	}

	@Test
	public void oversizedWriteIsRejectedBeforeReplacingTheFile(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("oversized.ini");
		Files.writeString(path, "original");
		String oversizedLine = "x".repeat(ConfigFileReader.MAX_FILE_BYTES);

		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, List.of(oversizedLine))
		);

		assertEquals("original", Files.readString(path));
	}

	@Test
	public void excessiveLineCountIsRejectedBeforeReplacingTheFile(@TempDir Path tempDir) throws Exception {
		Path maximumPath = tempDir.resolve("maximum-lines.ini");
		List<String> maximumLines = Collections.nCopies(ConfigFileReader.MAX_FILE_LINES, "");
		ConfigFileUtil.writeUsingTempFile(maximumPath, maximumLines);
		assertEquals(ConfigFileReader.MAX_FILE_LINES, ConfigFileReader.read(maximumPath).lines().size());

		Path path = tempDir.resolve("too-many-lines.ini");
		Files.writeString(path, "original");
		List<String> lines = Collections.nCopies(ConfigFileReader.MAX_FILE_LINES + 1, "");

		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, lines)
		);

		assertEquals("original", Files.readString(path));
	}

	@Test
	public void embeddedLineSeparatorsCountTowardTheReadableLimit(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("embedded-lines.ini");
		Files.writeString(path, "original");
		String line = "\n".repeat(ConfigFileReader.MAX_FILE_LINES);

		assertThrows(
			IllegalArgumentException.class,
			() -> ConfigFileUtil.writeUsingTempFile(path, List.of(line))
		);

		assertEquals("original", Files.readString(path));
	}
}
