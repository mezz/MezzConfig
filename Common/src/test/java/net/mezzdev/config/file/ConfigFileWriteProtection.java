package net.mezzdev.config.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Prevents replacement using real filesystem permissions, restoring them before temporary-directory cleanup. */
public interface ConfigFileWriteProtection extends AutoCloseable {
	static ConfigFileWriteProtection protect(Path path) throws IOException {
		Path directory = path.toAbsolutePath().getParent();
		PosixFileAttributeView posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
		if (posix != null) {
			var original = posix.readAttributes().permissions();
			var protectedPermissions = EnumSet.copyOf(original);
			protectedPermissions.removeAll(EnumSet.of(
				PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE
			));
			posix.setPermissions(protectedPermissions);
			if (Files.isWritable(directory)) {
				posix.setPermissions(original);
				assumeTrue(false, "The current user bypasses directory write permissions.");
			}
			return () -> posix.setPermissions(original);
		}
		DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class);
		assumeTrue(dos != null, "The filesystem must support POSIX permissions or DOS read-only attributes.");
		boolean original = dos.readAttributes().isReadOnly();
		dos.setReadOnly(true);
		return () -> dos.setReadOnly(original);
	}

	@Override
	void close() throws IOException;
}
