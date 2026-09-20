package net.mezzdev.config.forge;

import net.mezzdev.config.minecraft.MinecraftConfigRuntime;
import net.mezzdev.config.server.ServerConfigProtocol;
import net.mezzdev.config.server.ServerConfigRuntime;
import net.mezzdev.config.server.ServerConfigSyncChunkPayload;
import net.mezzdev.config.server.ServerIdentityPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

public final class ConfigForgeNetwork {
	private static final String VERSION = Integer.toString(ServerConfigProtocol.CHANNEL_VERSION);
	private final SimpleChannel channel;

	public ConfigForgeNetwork() {
		channel = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(ConfigForge.MOD_ID, "server_config"),
			() -> VERSION,
			ConfigForgeNetwork::acceptsVersion,
			ConfigForgeNetwork::acceptsVersion
		);
		channel.messageBuilder(ServerIdentityPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
			.encoder((payload, buffer) -> buffer.writeUUID(payload.serverId()))
			.decoder(buffer -> new ServerIdentityPayload(buffer.readUUID()))
			.consumerMainThread((payload, context) -> ServerConfigRuntime.handleServerIdentity(payload))
			.add();
		channel.messageBuilder(ServerConfigSyncChunkPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
			.encoder((payload, buffer) -> buffer.writeByteArray(payload.payload()))
			.decoder(buffer -> new ServerConfigSyncChunkPayload(buffer.readByteArray(ServerConfigSyncChunkPayload.MAX_NETWORK_PAYLOAD_LENGTH)))
			.consumerMainThread((payload, context) -> ServerConfigRuntime.handleSyncChunk(payload))
			.add();
		MinecraftConfigRuntime.setSender(new MinecraftConfigRuntime.Sender() {
			@Override
			public boolean sendIdentity(ServerPlayer player, UUID serverId) {
				return send(player, new ServerIdentityPayload(serverId));
			}

			@Override
			public boolean sendSync(ServerPlayer player, byte[] data) {
				return send(player, new ServerConfigSyncChunkPayload(data));
			}
		});
	}

	private boolean send(ServerPlayer player, Object payload) {
		if (!channel.isRemotePresent(player.connection.connection)) {
			return false;
		}
		channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
		return true;
	}

	private static boolean acceptsVersion(String version) {
		return VERSION.equals(version) || NetworkRegistry.ABSENT.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
	}
}
