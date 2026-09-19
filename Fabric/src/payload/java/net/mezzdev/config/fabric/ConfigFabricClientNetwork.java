package net.mezzdev.config.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mezzdev.config.minecraft.network.IdentityPacket;
import net.mezzdev.config.minecraft.network.SyncPacket;
import net.mezzdev.config.server.ServerConfigRuntime;

final class ConfigFabricClientNetwork {
	private ConfigFabricClientNetwork() {}
	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(
			IdentityPacket.TYPE,
			(payload, context) -> ServerConfigRuntime.handleServerIdentity(payload.toPayload())
		);
		ClientPlayNetworking.registerGlobalReceiver(
			SyncPacket.TYPE,
			(payload, context) -> ServerConfigRuntime.handleSyncChunk(payload.toPayload())
		);
	}
}
