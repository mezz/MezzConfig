package net.mezzdev.config.minecraft.network;

import net.mezzdev.config.minecraft.MinecraftConfigRuntime;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiPredicate;

public final class MinecraftPayloads {
	private MinecraftPayloads() {}

	public static void registerSender(BiPredicate<ServerPlayer, CustomPacketPayload> sender) {
		MinecraftConfigRuntime.setSender(new MinecraftConfigRuntime.Sender() {
			@Override
			public boolean sendIdentity(ServerPlayer player, UUID serverId) {
				return sender.test(player, new IdentityPacket(serverId));
			}

			@Override
			public boolean sendSync(ServerPlayer player, byte[] data) {
				return sender.test(player, new SyncPacket(data));
			}
		});
	}
}
