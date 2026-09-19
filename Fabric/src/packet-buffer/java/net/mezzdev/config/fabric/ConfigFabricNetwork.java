package net.mezzdev.config.fabric;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mezzdev.config.minecraft.MinecraftConfigRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

final class ConfigFabricNetwork {
	static final ResourceLocation IDENTITY = new ResourceLocation("mezz_config", "server_identity");
	static final ResourceLocation SYNC = new ResourceLocation("mezz_config", "server_config_sync");

	private ConfigFabricNetwork() {}

	static void register() {
		MinecraftConfigRuntime.setSender(new MinecraftConfigRuntime.Sender() {
			@Override
			public boolean sendIdentity(ServerPlayer player, UUID serverId) {
				if (!ServerPlayNetworking.canSend(player, IDENTITY)) {
					return false;
				}
				ServerPlayNetworking.send(player, IDENTITY, PacketByteBufs.create().writeUUID(serverId));
				return true;
			}

			@Override
			public boolean sendSync(ServerPlayer player, byte[] data) {
				if (!ServerPlayNetworking.canSend(player, SYNC)) {
					return false;
				}
				ServerPlayNetworking.send(player, SYNC, PacketByteBufs.create().writeByteArray(data));
				return true;
			}
		});
	}
}
