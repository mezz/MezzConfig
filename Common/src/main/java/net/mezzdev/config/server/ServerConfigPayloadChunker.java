package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.List;

final class ServerConfigPayloadChunker {
	static final int MAX_CHUNK_DATA_LENGTH = 30 * 1024;
	static final int MAX_NETWORK_PAYLOAD_LENGTH = MAX_CHUNK_DATA_LENGTH + 1;
	static final byte STATE_FIRST = 1;
	static final byte STATE_LAST = 2;
	private static final byte STATE_MASK = STATE_FIRST | STATE_LAST;

	private ServerConfigPayloadChunker() {

	}

	static List<byte[]> split(byte[] data) {
		data = ErrorUtil.checkNotNull(data, "data");
		int partCount = data.length / MAX_CHUNK_DATA_LENGTH;
		if (data.length % MAX_CHUNK_DATA_LENGTH != 0) {
			partCount++;
		}
		partCount = Math.max(1, partCount);
		List<byte[]> chunks = new ArrayList<>(partCount);
		for (int part = 0; part < partCount; part++) {
			int offset = part * MAX_CHUNK_DATA_LENGTH;
			int partLength = Math.min(MAX_CHUNK_DATA_LENGTH, data.length - offset);
			byte state = 0;
			if (part == 0) {
				state |= STATE_FIRST;
			}
			if (part == partCount - 1) {
				state |= STATE_LAST;
			}
			byte[] chunk = new byte[partLength + 1];
			chunk[0] = state;
			System.arraycopy(data, offset, chunk, 1, partLength);
			chunks.add(chunk);
		}
		return List.copyOf(chunks);
	}

	static byte[] validateAndCopy(byte[] payload) {
		payload = ErrorUtil.checkNotNull(payload, "payload");
		if (payload.length < 1 || payload.length > MAX_NETWORK_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("Invalid server config chunk length: " + payload.length);
		}
		byte state = payload[0];
		if ((state & ~STATE_MASK) != 0) {
			throw new IllegalArgumentException("Invalid server config chunk state: " + state);
		}
		return payload.clone();
	}
}
