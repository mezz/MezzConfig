package net.mezzdev.config.forge;

import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerIdentityPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;

public final class ConfigForgeNetwork {
	private static final int PROTOCOL_VERSION = 2;
	private final Channel<CustomPacketPayload> channel;

	public ConfigForgeNetwork() {
		this.channel = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(ConfigForge.MOD_ID, "server_config"))
			.networkProtocolVersion(PROTOCOL_VERSION)
			.optional()
			.payloadChannel()
			.play()
			.clientbound()
			.add(ServerIdentityPayload.TYPE, ServerIdentityPayload.STREAM_CODEC, this::handleServerIdentity)
			.add(ServerConfigSyncChunkPayload.TYPE, ServerConfigSyncChunkPayload.STREAM_CODEC, this::handleSync)
			.build();
		ServerConfigNetworking.setServerSender((player, payload) -> {
			if (!channel.isRemotePresent(player.connection.getConnection())) {
				return false;
			}
			Packet<?> packet = NetworkDirection.PLAY_TO_CLIENT.buildPacket(channel, payload);
			player.connection.send(packet);
			return true;
		});
	}

	private void handleServerIdentity(ServerIdentityPayload payload, CustomPayloadEvent.Context context) {
		context.setPacketHandled(true);
		context.enqueueWork(() -> ServerConfigRuntime.handleServerIdentity(payload));
	}

	private void handleSync(ServerConfigSyncChunkPayload payload, CustomPayloadEvent.Context context) {
		context.setPacketHandled(true);
		context.enqueueWork(() -> ServerConfigRuntime.handleSyncChunk(payload));
	}

}
