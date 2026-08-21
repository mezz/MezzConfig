package net.mezzdev.config.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ConfigFileUtil {
	public static final int MAX_BACKUPS = 5;
	private static boolean atomicMoveSupported = true;

	private ConfigFileUtil() {
	}

	public static void writeUsingTempFile(Path path, List<? extends CharSequence> lines) throws IOException {
		validateReadableContents(lines);
		Path tempFileDirectory = createParentDirectories(path);
		Path tempFile = Files.createTempFile(tempFileDirectory, null, null);
		try {
			Files.write(tempFile, lines);
			moveAtomicReplace(tempFile, path);
		} finally {
			if (Files.exists(tempFile)) {
				Files.delete(tempFile);
			}
		}
	}

	public static void validateReadableContents(List<? extends CharSequence> lines) {
		if (lines.size() > ConfigFileReader.MAX_FILE_LINES) {
			throw excessiveLineCount();
		}
		int lineSeparatorBytes = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
		long totalLines = lines.size();
		long totalBytes = 0;
		for (CharSequence line : lines) {
			totalLines += countEmbeddedLineSeparators(line);
			if (totalLines > ConfigFileReader.MAX_FILE_LINES) {
				throw excessiveLineCount();
			}
			totalBytes += line.toString().getBytes(StandardCharsets.UTF_8).length + lineSeparatorBytes;
			if (totalBytes > ConfigFileReader.MAX_FILE_BYTES) {
				throw new IllegalArgumentException(
					"Serialized config file exceeds the maximum supported size of " + ConfigFileReader.MAX_FILE_BYTES + " bytes."
				);
			}
		}
	}

	private static long countEmbeddedLineSeparators(CharSequence line) {
		long count = 0;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character == '\r') {
				count++;
				if (index + 1 < line.length() && line.charAt(index + 1) == '\n') {
					index++;
				}
			} else if (character == '\n') {
				count++;
			}
		}
		return count;
	}

	private static IllegalArgumentException excessiveLineCount() {
		return new IllegalArgumentException(
			"Serialized config file exceeds the maximum supported line count of " + ConfigFileReader.MAX_FILE_LINES + "."
		);
	}

	public static void moveAtomicReplace(Path source, Path target) throws IOException {
		if (atomicMoveSupported) {
			try {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (AtomicMoveNotSupportedException ignored) {
				atomicMoveSupported = false;
			}
		}
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	public static Path backUpFile(Path path, int maxBackups) throws IOException {
		if (maxBackups < 1) {
			throw new IllegalArgumentException("maxBackups must be positive.");
		}
		Path newestBackup = getBackupPath(path, 1);
		if (Files.exists(newestBackup) && Files.mismatch(path, newestBackup) == -1) {
			return newestBackup;
		}
		Path tempFileDirectory = createParentDirectories(path);
		Path stagedBackup = Files.createTempFile(tempFileDirectory, null, ".mezz-config-backup");
		try {
			Files.copy(path, stagedBackup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			for (int index = maxBackups; index > 1; index--) {
				Path previous = getBackupPath(path, index - 1);
				if (Files.exists(previous)) {
					Files.move(previous, getBackupPath(path, index), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			moveAtomicReplace(stagedBackup, newestBackup);
			return newestBackup;
		} finally {
			Files.deleteIfExists(stagedBackup);
		}
	}

	public static Path backUpFile(Path path) throws IOException {
		return backUpFile(path, MAX_BACKUPS);
	}

	private static Path createParentDirectories(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent == null) {
			return Path.of(".");
		}
		Files.createDirectories(parent);
		return parent;
	}

	public static Path getBackupPath(Path path, int index) {
		return path.resolveSibling(path.getFileName() + ".bak." + index);
	}
}
