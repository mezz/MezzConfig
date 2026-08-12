package net.mezzdev.config.forge;

import net.mezzdev.config.server.ServerConfigNetworking;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerConfigUpdateChunkPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
			.serverbound()
			.add(ServerConfigUpdateChunkPayload.TYPE, ServerConfigUpdateChunkPayload.STREAM_CODEC, this::handleUpdate)
			.clientbound()
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

	private void handleUpdate(ServerConfigUpdateChunkPayload payload, CustomPayloadEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player != null) {
			context.setPacketHandled(true);
			context.enqueueWork(() -> ServerConfigRuntime.handleUpdateChunk(player, payload));
		}
	}

	private void handleSync(ServerConfigSyncChunkPayload payload, CustomPayloadEvent.Context context) {
		context.setPacketHandled(true);
		context.enqueueWork(() -> ServerConfigRuntime.handleSyncChunk(payload));
	}

	public Channel<CustomPacketPayload> getChannel() {
		return channel;
	}
}
