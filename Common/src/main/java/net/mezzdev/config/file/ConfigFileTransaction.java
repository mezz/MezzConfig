package net.mezzdev.config.file;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigFileTransaction {
	private ConfigFileTransaction() {}

	public static void write(
		Map<Path, ? extends List<? extends CharSequence>> outputs,
		Set<Path> requiredAbsentTargets
	) throws IOException {
		Deque<StagedFile> stagedFiles = new ArrayDeque<>();
		List<CommittedFile> committedFiles = new ArrayList<>();
		Set<Path> normalizedTargets = new HashSet<>();
		Set<Path> normalizedRequiredAbsentTargets = new HashSet<>();
		for (Path path : requiredAbsentTargets) {
			normalizedRequiredAbsentTargets.add(normalize(path));
		}

		try {
			for (Map.Entry<Path, ? extends List<? extends CharSequence>> entry : outputs.entrySet()) {
				Path target = normalize(entry.getKey());
				if (!normalizedTargets.add(target)) {
					throw new IllegalArgumentException("A file transaction cannot write the same target more than once: " + target);
				}
				boolean requiredAbsent = normalizedRequiredAbsentTargets.contains(target);
				stagedFiles.add(stage(target, entry.getValue(), requiredAbsent));
			}

			while (!stagedFiles.isEmpty()) {
				committedFiles.add(stagedFiles.getFirst().commit());
				stagedFiles.removeFirst();
			}
		} catch (IOException | RuntimeException | Error failure) {
			rollback(committedFiles, failure);
			throw failure;
		} finally {
			for (StagedFile stagedFile : stagedFiles) {
				stagedFile.cleanup();
			}
		}
		for (CommittedFile committedFile : committedFiles) {
			committedFile.cleanup();
		}
	}

	private static StagedFile stage(
		Path target,
		List<? extends CharSequence> contents,
		boolean requiredAbsent
	) throws IOException {
		ConfigFileUtil.validateReadableContents(contents);
		if (requiredAbsent && Files.exists(target)) {
			throw new FileAlreadyExistsException(target.toString());
		}
		Path parent = target.getParent();
		if (parent == null) {
			parent = Path.of(".").toAbsolutePath().normalize();
		}
		Files.createDirectories(parent);

		Path stagedOutput = Files.createTempFile(parent, ".mezz-config-transaction-", ".tmp");
		Path rollbackFile = null;
		try {
			Files.write(stagedOutput, contents);
			force(stagedOutput);
			if (Files.exists(target)) {
				rollbackFile = Files.createTempFile(parent, ".mezz-config-rollback-", ".tmp");
				Files.copy(
					target,
					rollbackFile,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES
				);
				force(rollbackFile);
			}
			return new StagedFile(target, stagedOutput, rollbackFile, requiredAbsent);
		} catch (IOException | RuntimeException | Error failure) {
			try {
				Files.deleteIfExists(stagedOutput);
			} catch (IOException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			if (rollbackFile != null) {
				try {
					Files.deleteIfExists(rollbackFile);
				} catch (IOException cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
			}
			throw failure;
		}
	}

	private static void force(Path path) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
	}

	private static Path normalize(Path path) {
		return path.toAbsolutePath().normalize();
	}

	private static void rollback(List<CommittedFile> committedFiles, Throwable failure) {
		for (int index = committedFiles.size() - 1; index >= 0; index--) {
			try {
				committedFiles.get(index).rollback();
			} catch (IOException | RuntimeException | Error rollbackFailure) {
				// Leave a failed rollback's backup available for recovery.
				failure.addSuppressed(rollbackFailure);
			}
		}
	}

	private static void deleteQuietly(@Nullable Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {}
	}

	private record StagedFile(Path target, Path stagedOutput, @Nullable Path rollbackFile, boolean requiredAbsent) {
		private CommittedFile commit() throws IOException {
			if (requiredAbsent && Files.exists(target)) {
				throw new FileAlreadyExistsException(target.toString());
			}
			ConfigFileUtil.moveAtomicReplace(stagedOutput, target);
			return new CommittedFile(target, rollbackFile);
		}

		private void cleanup() {
			deleteQuietly(stagedOutput);
			deleteQuietly(rollbackFile);
		}
	}

	private record CommittedFile(Path target, @Nullable Path rollbackFile) {
		private void rollback() throws IOException {
			if (rollbackFile != null) {
				ConfigFileUtil.moveAtomicReplace(rollbackFile, target);
			} else {
				Files.deleteIfExists(target);
			}
		}

		private void cleanup() {
			deleteQuietly(rollbackFile);
		}
	}
}
