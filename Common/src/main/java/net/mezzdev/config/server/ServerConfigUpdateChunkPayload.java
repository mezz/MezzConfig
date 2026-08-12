package net.mezzdev.config.server;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerConfigUpdateChunkPayload(byte[] payload) implements CustomPacketPayload {
	public static final Type<ServerConfigUpdateChunkPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath("mezz_config", "server_config_update")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigUpdateChunkPayload> STREAM_CODEC = StreamCodec.ofMember(ServerConfigUpdateChunkPayload::write, ServerConfigUpdateChunkPayload::new);

	public ServerConfigUpdateChunkPayload {
		payload = ServerConfigPayloadChunker.validateAndCopy(payload);
	}

	private ServerConfigUpdateChunkPayload(RegistryFriendlyByteBuf buffer) {
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
