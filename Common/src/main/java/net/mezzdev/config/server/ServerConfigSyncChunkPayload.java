package net.mezzdev.config.server;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerConfigSyncChunkPayload(byte[] payload) implements CustomPacketPayload {
	public static final Type<ServerConfigSyncChunkPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath("mezz_config", "server_config_sync")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSyncChunkPayload> STREAM_CODEC = StreamCodec.ofMember(ServerConfigSyncChunkPayload::write, ServerConfigSyncChunkPayload::new);

	public ServerConfigSyncChunkPayload {
		payload = ServerConfigPayloadChunker.validateAndCopy(payload);
	}

	private ServerConfigSyncChunkPayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readByteArray(ServerConfigPayloadChunker.MAX_NETWORK_PAYLOAD_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeByteArray(payload);
	}

	byte[] payloadInternal() {
		return payload;
	}

	@Override
	public byte[] payload() {
		return payload.clone();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
