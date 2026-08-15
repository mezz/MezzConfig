package net.mezzdev.config.file;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigFileUtil {
	public static final int MAX_BACKUPS = 5;
	private static boolean atomicMoveSupported = true;

	private ConfigFileUtil() {
	}

	public static void writeUsingTempFile(Path path, Iterable<? extends CharSequence> lines) throws IOException {
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
