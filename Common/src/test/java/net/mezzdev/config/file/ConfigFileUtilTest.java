package net.mezzdev.config.file;

import com.sun.nio.file.ExtendedOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigFileUtilTest {
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@EnabledOnOs({OS.LINUX, OS.MAC})
	public void replacementPreservesOpenReadersAfterAnotherProvidersFallback(boolean useFallbackFirst, @TempDir Path tempDir) throws Exception {
		if (useFallbackFirst) {
			moveFromZipFileSystem(tempDir);
		}

		Path source = tempDir.resolve("staged.ini");
		Path target = tempDir.resolve("config.ini");
		Files.writeString(source, "new");
		Files.writeString(target, "old");
		try (var reader = Files.newInputStream(target)) {
			// Unix rename keeps existing readers on the old file after replacement.
			ConfigFileUtil.moveAtomicReplace(source, target);
			assertEquals("old", new String(reader.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals("new", Files.readString(target));
			assertFalse(Files.exists(source));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void replacementPreservesFilesUntilTargetReaderCloses(boolean useFallbackFirst, @TempDir Path tempDir) throws Exception {
		if (useFallbackFirst) {
			moveFromZipFileSystem(tempDir);
		}
		Path source = Files.writeString(tempDir.resolve("staged.ini"), "new");
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		try (var reader = Files.newInputStream(target)) {
			// Windows can reject replacement even when the reader permits delete sharing.
			assertThrows(FileSystemException.class, () -> ConfigFileUtil.moveAtomicReplace(source, target));
			assertEquals("old", new String(reader.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals("old", Files.readString(target));
			assertEquals("new", Files.readString(source));
		}

		ConfigFileUtil.moveAtomicReplace(source, target);
		assertEquals("new", Files.readString(target));
		assertFalse(Files.exists(source));
	}

	private static void moveFromZipFileSystem(Path tempDir) throws IOException {
		// A real move between providers requires the non-atomic copy/delete fallback.
		try (var archive = FileSystems.newFileSystem(tempDir.resolve("archive.zip"), Map.of("create", "true"))) {
			Path archivedSource = archive.getPath("/source.ini");
			Files.writeString(archivedSource, "archived");
			Path extracted = tempDir.resolve("extracted.ini");
			ConfigFileUtil.moveAtomicReplace(archivedSource, extracted);
			assertEquals("archived", Files.readString(extracted));
			assertFalse(Files.exists(archivedSource));
		}
	}

	@Test
	public void failedReplacementPreservesTheTargetAndCleansUpStagedOutput(@TempDir Path tempDir) throws Exception {
		Path target = Files.createDirectory(tempDir.resolve("config.ini"));
		Path child = Files.writeString(target.resolve("keep.txt"), "original");

		assertThrows(IOException.class, () -> ConfigFileUtil.writeUsingTempFile(target, List.of("new")));

		assertEquals("original", Files.readString(child));
		try (var files = Files.list(tempDir)) {
			assertEquals(List.of(target), files.toList());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void replacementWaitsForSharingLocks(boolean lockSource, @TempDir Path tempDir) throws Exception {
		Path source = Files.writeString(tempDir.resolve("staged.ini"), "new");
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		Path lockedPath = target;
		if (lockSource) {
			lockedPath = source;
		}
		var executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> replacement;
			try (var lock = FileChannel.open(lockedPath, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
				// Verify the real Windows sharing restriction before asking the utility to wait for it.
				assertThrows(FileSystemException.class, () -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
				CountDownLatch started = new CountDownLatch(1);
				replacement = executor.submit(() -> {
					started.countDown();
					ConfigFileUtil.moveAtomicReplace(source, target);
					return null;
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> replacement.get(100, TimeUnit.MILLISECONDS));
				assertEquals("old", Files.readString(target));
				assertEquals("new", Files.readString(source));
			}
			replacement.get(5, TimeUnit.SECONDS);
			assertEquals("new", Files.readString(target));
			assertFalse(Files.exists(source));
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void stagedWriteWaitsForAScannerThatDeniesWriteSharing(@TempDir Path tempDir) throws Exception {
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		Path staged = Files.createTempFile(tempDir, "staged-", ".tmp");
		byte[] bytes = "complete replacement".getBytes(StandardCharsets.UTF_8);
		var executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> save;
			// Model a scanner winning the race between createTempFile's close and the writer's reopen.
			try (var scanner = FileChannel.open(staged, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_WRITE)) {
				CountDownLatch started = new CountDownLatch(1);
				save = executor.submit(() -> {
					started.countDown();
					ConfigFileUtil.writeTempFile(staged, bytes);
					ConfigFileUtil.moveAtomicReplace(staged, target);
					return null;
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> save.get(100, TimeUnit.MILLISECONDS));
				assertEquals("old", Files.readString(target));
				assertEquals(0, Files.size(staged));
			}
			save.get(5, TimeUnit.SECONDS);
			assertEquals("complete replacement", Files.readString(target));
			assertFalse(Files.exists(staged));
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void cleanupWaitsForAScannerThatDeniesDeleteSharing(@TempDir Path tempDir) throws Exception {
		Path staged = Files.createTempFile(tempDir, "staged-", ".tmp");
		var executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> cleanup;
			try (var scanner = FileChannel.open(staged, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
				CountDownLatch started = new CountDownLatch(1);
				cleanup = executor.submit(() -> {
					started.countDown();
					ConfigFileUtil.deleteIfExists(staged);
					return null;
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> cleanup.get(100, TimeUnit.MILLISECONDS));
				assertTrue(Files.exists(staged));
			}
			cleanup.get(5, TimeUnit.SECONDS);
			assertFalse(Files.exists(staged));
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void backupReadsWaitForAScanner(boolean transaction, @TempDir Path tempDir) throws Exception {
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		var executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> backup;
			try (var scanner = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ)) {
				CountDownLatch started = new CountDownLatch(1);
				backup = executor.submit(() -> {
					started.countDown();
					if (transaction) {
						ConfigFileTransaction.write(Map.of(target, List.of("new")), Set.of());
					} else {
						ConfigFileUtil.backUpFile(target);
					}
					return null;
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> backup.get(100, TimeUnit.MILLISECONDS));
			}
			backup.get(5, TimeUnit.SECONDS);
			if (transaction) {
				assertEquals("new" + System.lineSeparator(), Files.readString(target));
				try (var files = Files.list(tempDir)) {
					assertEquals(List.of(target), files.toList());
				}
			} else {
				assertEquals("old", Files.readString(target));
				assertEquals("old", Files.readString(ConfigFileUtil.getBackupPath(target, 1)));
			}
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void persistentSharingLockPreservesTheConfigAndCleansUpStagedOutput(@TempDir Path tempDir) throws Exception {
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		try (var lock = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			assertThrows(FileSystemException.class, () -> ConfigFileUtil.writeUsingTempFile(target, List.of("new")));
			assertEquals("old", Files.readString(target));
			try (var files = Files.list(tempDir)) {
				assertEquals(List.of(target), files.toList());
			}
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void backupRotationPreservesALockedDestination(@TempDir Path tempDir) throws Exception {
		Path target = Files.writeString(tempDir.resolve("config.ini"), "current");
		Path newestBackup = Files.writeString(ConfigFileUtil.getBackupPath(target, 1), "previous");
		Path oldestBackup = Files.writeString(ConfigFileUtil.getBackupPath(target, 2), "oldest");
		try (var lock = FileChannel.open(oldestBackup, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			assertThrows(FileSystemException.class, () -> ConfigFileUtil.backUpFile(target, 2));
			assertEquals("current", Files.readString(target));
			assertEquals("previous", Files.readString(newestBackup));
			assertEquals("oldest", Files.readString(oldestBackup));
			try (var files = Files.list(tempDir)) {
				assertEquals(3, files.count());
			}
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	@Timeout(10)
	public void interruptedRetryPreservesBothFiles(@TempDir Path tempDir) throws Exception {
		Path source = Files.writeString(tempDir.resolve("staged.ini"), "new");
		Path target = Files.writeString(tempDir.resolve("config.ini"), "old");
		try (var lock = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			Thread.currentThread().interrupt();
			try {
				InterruptedIOException failure = assertThrows(
					InterruptedIOException.class,
					() -> ConfigFileUtil.moveAtomicReplace(source, target)
				);
				assertTrue(Thread.currentThread().isInterrupted());
				assertTrue(failure.getSuppressed()[0] instanceof FileSystemException);
			} finally {
				Thread.interrupted();
			}
			assertEquals("new", Files.readString(source));
			assertEquals("old", Files.readString(target));
		}
	}

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
