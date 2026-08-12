package net.mezzdev.config.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ServerConfigPayloadReassembler {
	private final List<byte[]> receivedBuffers = new ArrayList<>();
	private int receivedLength;

	public synchronized Optional<byte[]> accept(byte[] payload) {
		payload = ServerConfigPayloadChunker.validateAndCopy(payload);
		try {
			byte state = payload[0];
			boolean first = (state & ServerConfigPayloadChunker.STATE_FIRST) != 0;
			boolean last = (state & ServerConfigPayloadChunker.STATE_LAST) != 0;
			if (first) {
				clear();
			} else if (receivedBuffers.isEmpty()) {
				throw new IllegalArgumentException("Received a server config chunk without a first chunk.");
			}

			byte[] content = new byte[payload.length - 1];
			System.arraycopy(payload, 1, content, 0, content.length);
			receivedLength = Math.addExact(receivedLength, content.length);
			receivedBuffers.add(content);
			if (!last) {
				return Optional.empty();
			}

			byte[] result = new byte[receivedLength];
			int offset = 0;
			for (byte[] buffer : receivedBuffers) {
				System.arraycopy(buffer, 0, result, offset, buffer.length);
				offset += buffer.length;
			}
			clear();
			return Optional.of(result);
		} catch (RuntimeException e) {
			clear();
			throw e;
		}
	}

	public synchronized void clear() {
		receivedBuffers.clear();
		receivedLength = 0;
	}
}
