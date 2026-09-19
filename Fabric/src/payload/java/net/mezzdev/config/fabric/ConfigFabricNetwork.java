package net.mezzdev.config.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mezzdev.config.minecraft.network.MinecraftPayloads;

final class ConfigFabricNetwork {
	private ConfigFabricNetwork() {}
	static void register() {
		ConfigFabricPayloadRegistry.register();
		MinecraftPayloads.registerSender((player, payload) -> {
			if (ServerPlayNetworking.canSend(player, payload.type())) {
				ServerPlayNetworking.send(player, payload);
				return true;
			}
			return false;
		});
	}
}
