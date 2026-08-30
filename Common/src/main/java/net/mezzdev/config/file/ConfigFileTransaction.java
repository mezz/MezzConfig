package net.mezzdev.config.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
		List<StagedFile> stagedFiles = new ArrayList<>();
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

			for (StagedFile stagedFile : stagedFiles) {
				stagedFile.commit();
			}
		} catch (IOException | RuntimeException | Error failure) {
			rollback(stagedFiles, failure);
			throw failure;
		} finally {
			cleanup(stagedFiles);
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
		boolean targetExisted = Files.exists(target);
		try {
			Files.write(stagedOutput, contents);
			force(stagedOutput);
			if (targetExisted) {
				rollbackFile = Files.createTempFile(parent, ".mezz-config-rollback-", ".tmp");
				Files.copy(
					target,
					rollbackFile,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES
				);
				force(rollbackFile);
			}
			return new StagedFile(target, stagedOutput, rollbackFile, targetExisted, requiredAbsent);
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

	private static void rollback(List<StagedFile> stagedFiles, Throwable failure) {
		for (int index = stagedFiles.size() - 1; index >= 0; index--) {
			try {
				stagedFiles.get(index).rollback();
			} catch (IOException | RuntimeException | Error rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
		}
	}

	private static void cleanup(List<StagedFile> stagedFiles) {
		for (StagedFile stagedFile : stagedFiles) {
			stagedFile.cleanup();
		}
	}

	private static final class StagedFile {
		private final Path target;
		private final boolean targetExisted;
		private final boolean requiredAbsent;
		private Path stagedOutput;
		private Path rollbackFile;
		private boolean committed;
		private boolean preserveRollbackFile;

		private StagedFile(
			Path target,
			Path stagedOutput,
			Path rollbackFile,
			boolean targetExisted,
			boolean requiredAbsent
		) {
			this.target = target;
			this.stagedOutput = stagedOutput;
			this.rollbackFile = rollbackFile;
			this.targetExisted = targetExisted;
			this.requiredAbsent = requiredAbsent;
		}

		private void commit() throws IOException {
			if (requiredAbsent && Files.exists(target)) {
				throw new FileAlreadyExistsException(target.toString());
			}
			ConfigFileUtil.moveAtomicReplace(stagedOutput, target);
			stagedOutput = null;
			committed = true;
		}

		private void rollback() throws IOException {
			if (!committed) {
				return;
			}
			if (targetExisted) {
				try {
					ConfigFileUtil.moveAtomicReplace(rollbackFile, target);
				} catch (IOException | RuntimeException | Error failure) {
					preserveRollbackFile = true;
					throw failure;
				}
				rollbackFile = null;
			} else {
				Files.deleteIfExists(target);
			}
			committed = false;
		}

		private void cleanup() {
			deleteQuietly(stagedOutput);
			if (!preserveRollbackFile) {
				deleteQuietly(rollbackFile);
			}
		}

		private static void deleteQuietly(Path path) {
			if (path == null) {
				return;
			}
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {}
		}
	}
}
