package net.mezzdev.config.server;

public record ServerConfigSyncChunkPayload(byte[] payload) {
	public static final int MAX_NETWORK_PAYLOAD_LENGTH = ServerConfigPayloadChunker.MAX_NETWORK_PAYLOAD_LENGTH;
	public ServerConfigSyncChunkPayload {
		payload = ServerConfigPayloadChunker.validateAndCopy(payload);
	}

	byte[] payloadInternal() {
		return payload;
	}

	@Override
	public byte[] payload() {
		return payload.clone();
	}
}
