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
		UUID first = ServerIdentityStore.getOrCreate(worldRoot);
		UUID second = ServerIdentityStore.getOrCreate(worldRoot);

		assertEquals(first, second);
		assertEquals(first.toString(), Files.readString(ServerIdentityStore.getPath(worldRoot)).trim());
	}

	@Test
	public void invalidIdentityIsBackedUpAndReplaced(@TempDir Path worldRoot) throws IOException {
		Path path = ServerIdentityStore.getPath(worldRoot);
		Files.createDirectories(path.getParent());
		Files.writeString(path, "not-a-uuid");

		UUID serverId = ServerIdentityStore.getOrCreate(worldRoot);

		assertEquals(serverId.toString(), Files.readString(path).trim());
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
		assertEquals("not-a-uuid", Files.readString(ConfigFileUtil.getBackupPath(path, 1)));
	}
}
