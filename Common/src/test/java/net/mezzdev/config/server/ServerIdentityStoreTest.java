package net.mezzdev.config.server;

import net.mezzdev.config.file.ConfigFileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerIdentityStoreTest {
	@Test
	public void identityPersistsWithTheWorld(@TempDir Path worldRoot) throws IOException {
		// Setup: a world has no stored server identity yet.
		Path identityPath = ServerIdentityStore.getPath(worldRoot);

		// Operation: request its identity twice.
		UUID first = ServerIdentityStore.getOrCreate(worldRoot);
		UUID second = ServerIdentityStore.getOrCreate(worldRoot);

		// Assertions: both calls return the same identity and persist it with the world.
		assertEquals(first, second);
		assertEquals(first.toString(), Files.readString(identityPath).trim());
	}

	@Test
	public void invalidIdentityIsBackedUpAndReplaced(@TempDir Path worldRoot) throws IOException {
		// Setup: the world's identity file contains malformed UUID text.
		Path path = ServerIdentityStore.getPath(worldRoot);
		Files.createDirectories(path.getParent());
		Files.writeString(path, "not-a-uuid");

		// Operation: request a usable identity for the world.
		UUID serverId = ServerIdentityStore.getOrCreate(worldRoot);

		// Assertions: a new UUID replaces the malformed text after the original file is backed up.
		assertEquals(serverId.toString(), Files.readString(path).trim());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertEquals("not-a-uuid", Files.readString(ConfigFileUtil.getBackupPath(path, 1)));
	}
}
