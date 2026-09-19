package net.mezzdev.config.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.mezzdev.config.minecraft.network.IdentityPacket;
import net.mezzdev.config.minecraft.network.SyncPacket;

final class ConfigFabricPayloadRegistry {
	private ConfigFabricPayloadRegistry() {}
	static void register() {
		PayloadTypeRegistry.playS2C().register(IdentityPacket.TYPE, IdentityPacket.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPacket.TYPE, SyncPacket.STREAM_CODEC);
	}
}
