package net.mezzdev.config.test.client;

import net.mezzdev.config.client.ClientWorldConfigPathUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

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
	public void getServerPathUsesSanitizedAddressWhenAddressIsMalformed() {
		// Setup: the server address has a port that cannot be parsed.
		String serverName = "Test Server";
		String serverAddress = "play.example.com:not-a-port";

		// Operation: build the client-world config directory when normal address parsing fails.
		Path path = ClientWorldConfigPathUtil.getServerPath(serverName, serverAddress);

		// Assertions: the supplied address still produces a readable, deterministic path.
		assertEquals(getServerPath("Test Server (play_example_com_not-a-port)"), path);
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
	public void getServerPathUsesStableServerIdentity() {
		UUID serverId = UUID.fromString("dc2a86e0-fc9f-4ae4-aa7a-718c46817a3e");

		Path path = ClientWorldConfigPathUtil.getServerPath(serverId);

		assertEquals(getServerPath(serverId.toString()), path);
	}

	@Test
	public void defaultWorldPathIsSeparateFromPlayerWorlds() {
		assertEquals(
			tempDir.resolve("world").resolve("default"),
			ClientWorldConfigPathUtil.getDefaultWorldPath(tempDir)
		);
	}

	private static Path getServerPath(String pathName) {
		return ClientWorldConfigPathUtil.getServerDirPath()
			.resolve(pathName);
	}
}
