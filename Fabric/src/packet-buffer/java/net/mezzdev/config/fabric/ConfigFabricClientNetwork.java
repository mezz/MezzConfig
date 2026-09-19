package net.mezzdev.config.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerIdentityPayload;

final class ConfigFabricClientNetwork {
	private ConfigFabricClientNetwork() {}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ConfigFabricNetwork.IDENTITY, (client, handler, buffer, sender) -> {
			ServerIdentityPayload payload = new ServerIdentityPayload(buffer.readUUID());
			client.execute(() -> ServerConfigRuntime.handleServerIdentity(payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(ConfigFabricNetwork.SYNC, (client, handler, buffer, sender) -> {
			ServerConfigSyncChunkPayload payload = new ServerConfigSyncChunkPayload(
				buffer.readByteArray(ServerConfigSyncChunkPayload.MAX_NETWORK_PAYLOAD_LENGTH)
			);
			client.execute(() -> ServerConfigRuntime.handleSyncChunk(payload));
		});
	}
}
