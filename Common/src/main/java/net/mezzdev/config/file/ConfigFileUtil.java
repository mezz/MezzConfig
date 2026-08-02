package net.mezzdev.config.file;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ConfigFileUtil {
	private static boolean atomicMoveSupported = true;

	private ConfigFileUtil() {
	}

	public static void writeUsingTempFile(Path path, Iterable<? extends CharSequence> lines) throws IOException {
		Path parent = path.getParent();
		Path tempFileDirectory = Path.of(".");
		if (parent != null) {
			Files.createDirectories(parent);
			tempFileDirectory = parent;
		}
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
}
