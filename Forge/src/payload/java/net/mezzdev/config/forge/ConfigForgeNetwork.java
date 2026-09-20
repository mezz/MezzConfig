package net.mezzdev.config.forge;

import net.mezzdev.config.minecraft.network.IdentityPacket;
import net.mezzdev.config.minecraft.network.MinecraftPayloads;
import net.mezzdev.config.minecraft.network.SyncPacket;
import net.mezzdev.config.server.ServerConfigProtocol;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;

public final class ConfigForgeNetwork {
	private final Channel<CustomPacketPayload> channel;

	public ConfigForgeNetwork() {
		channel = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(ConfigForge.MOD_ID, "server_config"))
			.networkProtocolVersion(ServerConfigProtocol.CHANNEL_VERSION)
			.optional()
			.payloadChannel()
			.play()
			.clientbound()
			.add(IdentityPacket.TYPE, IdentityPacket.STREAM_CODEC, this::handleServerIdentity)
			.add(SyncPacket.TYPE, SyncPacket.STREAM_CODEC, this::handleSync)
			.build();
		MinecraftPayloads.registerSender((player, payload) -> {
			if (!channel.isRemotePresent(player.connection.getConnection())) {
				return false;
			}
			Packet<?> packet = NetworkDirection.PLAY_TO_CLIENT.buildPacket(channel, payload);
			player.connection.send(packet);
			return true;
		});
	}

	private void handleServerIdentity(IdentityPacket payload, CustomPayloadEvent.Context context) {
		context.setPacketHandled(true);
		context.enqueueWork(() -> ServerConfigRuntime.handleServerIdentity(payload.toPayload()));
	}

	private void handleSync(SyncPacket payload, CustomPayloadEvent.Context context) {
		context.setPacketHandled(true);
		context.enqueueWork(() -> ServerConfigRuntime.handleSyncChunk(payload.toPayload()));
	}
}
