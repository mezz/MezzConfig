package net.mezzdev.config.file;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public final class ConfigFileUtil {
	public static final int MAX_BACKUPS = 5;
	private static final int IO_RETRIES = 5;
	private static final long IO_RETRY_DELAY_MILLIS = 50;

	private ConfigFileUtil() {
	}

	public static void writeUsingTempFile(Path path, List<? extends CharSequence> lines) throws IOException {
		writeUsingTempFileAndGetFingerprint(path, lines);
	}

	static String writeUsingTempFileAndGetFingerprint(Path path, List<? extends CharSequence> lines) throws IOException {
		return writeUsingTempFileAndGetFingerprint(path, lines, null);
	}

	static String writeUsingTempFileAndGetFingerprint(
		Path path,
		List<? extends CharSequence> lines,
		@Nullable String loadedFingerprint
	) throws IOException {
		validateReadableContents(lines);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		MessageDigest digest = ConfigFileReader.newFingerprintDigest();
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
			new DigestOutputStream(output, digest),
			StandardCharsets.UTF_8
		))) {
			for (CharSequence line : lines) {
				writer.append(line);
				writer.newLine();
			}
		}
		byte[] bytes = output.toByteArray();
		String fingerprint = ConfigFileReader.finishFingerprint(digest);
		// A startup refresh can compare with the bytes already read while loading the config.
		boolean unchanged;
		if (loadedFingerprint == null) {
			unchanged = hasSameContents(path, bytes);
		} else {
			unchanged = fingerprint.equals(loadedFingerprint);
		}
		if (unchanged) {
			return fingerprint;
		}
		Path tempFileDirectory = createParentDirectories(path);
		Path tempFile = Files.createTempFile(tempFileDirectory, null, null);
		try {
			writeTempFile(tempFile, bytes);
			moveAtomicReplace(tempFile, path);
			return fingerprint;
		} catch (IOException | RuntimeException | Error failure) {
			deleteTempFile(tempFile, failure);
			throw failure;
		}
	}

	static void writeTempFile(Path tempFile, byte[] bytes) throws IOException {
		// createTempFile closes its handle. A scanner can deny write sharing before this reopen.
		withRetry(tempFile, () -> {
			try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				channel.force(true);
			}
			return tempFile;
		});
	}

	static void writeTempFile(Path tempFile, List<? extends CharSequence> lines) throws IOException {
		withRetry(tempFile, () -> {
			Files.write(tempFile, lines);
			force(tempFile);
			return tempFile;
		});
	}

	static void copyToTempFile(Path source, Path tempFile) throws IOException {
		withRetry(source, () -> {
			Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			force(tempFile);
			return tempFile;
		});
	}

	private static void force(Path path) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
	}

	private static boolean hasSameContents(Path path, byte[] bytes) {
		try (InputStream input = Files.newInputStream(path)) {
			return Arrays.equals(bytes, input.readNBytes(bytes.length + 1));
		} catch (IOException ignored) {
			// Comparing is optional: missing or unreadable files still get a normal save attempt.
			return false;
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

	/**
	 * Replaces the target atomically when supported by this move's filesystem/provider.
	 * Falls back to a non-atomic replacement only when atomic moving or replacing is unsupported.
	 * Windows sharing/access failures use a limited number of retries with increasing delays.
	 * They must not trigger the non-atomic fallback, which can delete the target before the rename.
	 * Atomic visibility does not guarantee that the directory entry survives power loss.
	 */
	public static void moveAtomicReplace(Path source, Path target) throws IOException {
		try {
			withRetry(source, () -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
		} catch (AtomicMoveNotSupportedException | FileAlreadyExistsException unsupported) {
			// Support depends on this move, not on the operating system or earlier saves.
			try {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException failure) {
				failure.addSuppressed(unsupported);
				throw failure;
			}
		}
	}

	/** Only retry operations that can safely restart; never retry a non-atomic replacement. */
	private static <T> T withRetry(Path path, IoOperation<T> operation) throws IOException {
		for (int retry = 0;; retry++) {
			try {
				return operation.run();
			} catch (FileSystemException failure) {
				if (retry >= IO_RETRIES || !isRetryableFailure(path, failure)) {
					throw failure;
				}
				try {
					Thread.sleep(IO_RETRY_DELAY_MILLIS << retry);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					InterruptedIOException aborted = new InterruptedIOException("Interrupted while accessing " + path);
					aborted.initCause(interrupted);
					aborted.addSuppressed(failure);
					throw aborted;
				}
			}
		}
	}

	private static boolean isRetryableFailure(Path path, FileSystemException failure) {
		// Retry temporary Windows file-access conflicts: an editor or antivirus scanner may
		// briefly hold a file open in a way that blocks our operation. Retrying gives that
		// process time to release the file instead of immediately failing the config operation.
		// OpenJDK maps access denial to AccessDeniedException and sharing violations to plain
		// FileSystemException. The native error code is unavailable and the reason is localized.
		// These types can also indicate permanent failures, so the number of retries is limited.
		return FileSystems.getDefault().getSeparator().equals("\\") &&
			path.getFileSystem() == FileSystems.getDefault() &&
			(failure instanceof AccessDeniedException || failure.getClass() == FileSystemException.class);
	}

	@FunctionalInterface
	private interface IoOperation<T> {
		T run() throws IOException;
	}

	public static Path backUpFile(Path path, int maxBackups) throws IOException {
		if (maxBackups < 1) {
			throw new IllegalArgumentException("maxBackups must be positive.");
		}
		Path newestBackup = getBackupPath(path, 1);
		if (Files.exists(newestBackup) && withRetry(path, () -> Files.mismatch(path, newestBackup)) == -1) {
			return newestBackup;
		}
		Path tempFileDirectory = createParentDirectories(path);
		Path stagedBackup = Files.createTempFile(tempFileDirectory, null, ".mezz-config-backup");
		try {
			withRetry(path, () -> Files.copy(path, stagedBackup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES));
			for (int index = maxBackups; index > 1; index--) {
				Path previous = getBackupPath(path, index - 1);
				if (Files.exists(previous)) {
					moveAtomicReplace(previous, getBackupPath(path, index));
				}
			}
			moveAtomicReplace(stagedBackup, newestBackup);
			return newestBackup;
		} catch (IOException | RuntimeException | Error failure) {
			deleteTempFile(stagedBackup, failure);
			throw failure;
		}
	}

	private static void deleteTempFile(Path path, Throwable failure) {
		try {
			deleteIfExists(path);
		} catch (IOException cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
	}

	static void deleteIfExists(Path path) throws IOException {
		withRetry(path, () -> Files.deleteIfExists(path));
	}

	public static Path backUpFile(Path path) throws IOException {
		return backUpFile(path, MAX_BACKUPS);
	}

	private static Path createParentDirectories(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent == null) {
			return Path.of(".");
		}
		withRetry(parent, () -> Files.createDirectories(parent));
		return parent;
	}

	public static Path getBackupPath(Path path, int index) {
		return path.resolveSibling(path.getFileName() + ".bak." + index);
	}
}
