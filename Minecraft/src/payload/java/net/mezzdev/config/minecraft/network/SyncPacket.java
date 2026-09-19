package net.mezzdev.config.minecraft.network;

import net.mezzdev.config.server.ServerConfigSyncChunkPayload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncPacket(byte[] payload) implements CustomPacketPayload {
	public static final Type<SyncPacket> TYPE = new Type<>(
		PacketIds.create("server_config_sync")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncPacket> STREAM_CODEC = StreamCodec.ofMember(SyncPacket::write, SyncPacket::new);

	public SyncPacket {
		payload = new ServerConfigSyncChunkPayload(payload).payload();
	}

	private SyncPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readByteArray(ServerConfigSyncChunkPayload.MAX_NETWORK_PAYLOAD_LENGTH));
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

	public ServerConfigSyncChunkPayload toPayload() {
		return new ServerConfigSyncChunkPayload(payload);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
