package net.mezzdev.config.test.plugin;

import net.mezzdev.config.plugin.ClientWorldConfigPathUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientWorldConfigPathUtilTest {
	@TempDir
	Path tempDir;

	@Test
	public void getServerPathUsesHostname() {
		// Setup: the server address is a hostname with mixed casing.
		String serverName = "Test Server: 1";
		String serverAddress = "Play.Example.com";

		// Operation: build the client-world config directory for this server.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress);

		// Assertions: the server name and normalized hostname are used for a human-readable path.
		assertEquals(getServerPath("Test Server_ 1 (play_example_com)"), path);
	}

	@Test
	public void getServerPathUsesHostnameWithNonDefaultPort() {
		// Setup: the server address is a hostname with a non-default port.
		String serverName = "Test Server";
		String serverAddress = "play.example.com:25566";

		// Operation: build the client-world config directory for this server.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress);

		// Assertions: non-default ports are included so different server instances do not collide.
		assertEquals(getServerPath("Test Server (play_example_com 25566)"), path);
	}

	@Test
	public void getServerPathOmitsDefaultPort() {
		// Setup: the server address includes the default Minecraft port.
		String serverName = "Test Server";
		String serverAddress = "play.example.com:25565";

		// Operation: build the client-world config directory for this server.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress);

		// Assertions: the default port is omitted to keep the normal path stable.
		assertEquals(getServerPath("Test Server (play_example_com)"), path);
	}

	@Test
	public void getServerPathUsesLiteralIpv6Address() {
		// Setup: the server address is a bracketed IPv6 address.
		String serverName = "Test Server";
		String serverAddress = "[2001:db8::1]";

		// Operation: build the client-world config directory for this server.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress);

		// Assertions: brackets are removed and the address is sanitized for filesystem use.
		assertEquals(getServerPath("Test Server (2001_db8__1)"), path);
	}

	@Test
	public void getServerPathUsesNameForLanServer() {
		// Setup: LAN server ports are often dynamic, so the address is not a stable identity.
		String serverName = "LAN Server";
		String serverAddress = "192.0.2.1:54321";

		// Operation: build the client-world config directory for this LAN server.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress, true);

		// Assertions: LAN servers use the advertised server name and connection type.
		assertEquals(getServerPath("LAN Server (LAN connection)"), path);
	}

	@Test
	public void getServerPathPreservesExistingLegacyPath() throws IOException {
		// Setup: an old JEI-style hashed server path already exists under the plugin config directory.
		String serverName = "Test Server";
		String serverAddress = "play.example.com";
		Path legacyPath = getLegacyServerPath(serverName, serverAddress);
		Files.createDirectories(tempDir.resolve(legacyPath));

		// Operation: build the client-world config directory with a plugin root available for legacy detection.
		Path path = ClientWorldConfigPathUtil.getServerPath(tempDir, serverName, serverAddress);

		// Assertions: the existing legacy path wins so migrated projects do not silently split config state.
		assertEquals(legacyPath, path);
	}

	private static Path getLegacyServerPath(String serverName, String serverAddress) {
		String ipHashHex = Integer.toHexString(serverAddress.hashCode());
		String name = "%s_%s".formatted(serverName, ipHashHex);
		name = String.join("_", name.split("[^\\w-]"));
		return getServerPath(name);
	}

	private static Path getServerPath(String pathName) {
		return ClientWorldConfigPathUtil.getServerDirPath()
			.resolve(pathName);
	}
}
