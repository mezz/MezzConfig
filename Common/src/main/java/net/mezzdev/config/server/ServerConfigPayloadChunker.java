package net.mezzdev.config.server;

import net.mezzdev.config.util.ErrorUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class ServerConfigPayloadChunker {
	static final int MAX_REASSEMBLED_PAYLOAD_LENGTH = 1024 * 1024;
	static final int MAX_FRAGMENT_COUNT = 64;
	static final int MAX_CHUNK_DATA_LENGTH = 30 * 1024;
	private static final int MAGIC = ByteBuffer.wrap("MZCF".getBytes(StandardCharsets.US_ASCII)).getInt();
	private static final byte VERSION = 3;
	private static final int HEADER_LENGTH = Integer.BYTES + Byte.BYTES + Long.BYTES +
		(Integer.BYTES * 3);
	static final int MAX_NETWORK_PAYLOAD_LENGTH = HEADER_LENGTH + MAX_CHUNK_DATA_LENGTH;
	private static final AtomicLong NEXT_MESSAGE_ID = new AtomicLong();

	private ServerConfigPayloadChunker() {

	}

	static List<byte[]> split(byte[] data) {
		long messageId = NEXT_MESSAGE_ID.updateAndGet(ServerConfigPayloadChunker::getNextMessageId);
		return split(data, messageId);
	}

	static List<byte[]> split(byte[] data, long messageId) {
		data = ErrorUtil.checkNotNull(data, "data");
		if (data.length > MAX_REASSEMBLED_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("Server config payload exceeds the maximum length of " +
				MAX_REASSEMBLED_PAYLOAD_LENGTH + " bytes: " + data.length);
		}
		if (messageId <= 0) {
			throw new IllegalArgumentException("messageId must be positive.");
		}
		int fragmentCount = Math.max(1, (data.length + MAX_CHUNK_DATA_LENGTH - 1) / MAX_CHUNK_DATA_LENGTH);
		if (fragmentCount > MAX_FRAGMENT_COUNT) {
			throw new IllegalArgumentException("Server config payload requires too many fragments: " + fragmentCount);
		}
		List<byte[]> chunks = new ArrayList<>(fragmentCount);
		for (int fragmentIndex = 0; fragmentIndex < fragmentCount; fragmentIndex++) {
			int offset = fragmentIndex * MAX_CHUNK_DATA_LENGTH;
			int contentLength = Math.min(MAX_CHUNK_DATA_LENGTH, data.length - offset);
			byte[] chunk = new byte[HEADER_LENGTH + contentLength];
			ByteBuffer header = ByteBuffer.wrap(chunk);
			header.putInt(MAGIC);
			header.put(VERSION);
			header.putLong(messageId);
			header.putInt(fragmentIndex);
			header.putInt(fragmentCount);
			header.putInt(data.length);
			System.arraycopy(data, offset, chunk, HEADER_LENGTH, contentLength);
			chunks.add(chunk);
		}
		return List.copyOf(chunks);
	}

	static byte[] validateAndCopy(byte[] payload) {
		payload = ErrorUtil.checkNotNull(payload, "payload");
		parse(payload);
		return payload.clone();
	}

	static Fragment parse(byte[] payload) {
		payload = ErrorUtil.checkNotNull(payload, "payload");
		if (payload.length < HEADER_LENGTH || payload.length > MAX_NETWORK_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("Invalid server config fragment length: " + payload.length);
		}
		ByteBuffer header = ByteBuffer.wrap(payload);
		int magic = header.getInt();
		if (magic != MAGIC) {
			throw new IllegalArgumentException("Invalid server config fragment magic.");
		}
		byte version = header.get();
		if (version != VERSION) {
			throw new IllegalArgumentException("Unsupported server config fragment version: " + version);
		}
		long messageId = header.getLong();
		int fragmentIndex = header.getInt();
		int fragmentCount = header.getInt();
		int totalLength = header.getInt();
		if (messageId <= 0) {
			throw new IllegalArgumentException("Invalid server config message id: " + messageId);
		}
		if (fragmentCount < 1 || fragmentCount > MAX_FRAGMENT_COUNT) {
			throw new IllegalArgumentException("Invalid server config fragment count: " + fragmentCount);
		}
		if (fragmentIndex < 0 || fragmentIndex >= fragmentCount) {
			throw new IllegalArgumentException("Invalid server config fragment index: " + fragmentIndex);
		}
		if (totalLength < 0 || totalLength > MAX_REASSEMBLED_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("Invalid server config message length: " + totalLength);
		}
		int expectedFragmentCount = Math.max(1,
			(totalLength + MAX_CHUNK_DATA_LENGTH - 1) / MAX_CHUNK_DATA_LENGTH);
		if (fragmentCount != expectedFragmentCount) {
			throw new IllegalArgumentException("Server config fragment count does not match its declared message length.");
		}
		int contentLength = payload.length - HEADER_LENGTH;
		int expectedContentLength = Math.min(
			MAX_CHUNK_DATA_LENGTH,
			totalLength - (fragmentIndex * MAX_CHUNK_DATA_LENGTH)
		);
		if (contentLength != expectedContentLength) {
			throw new IllegalArgumentException("Server config fragment length does not match its index and declared message length.");
		}
		return new Fragment(
			messageId,
			fragmentIndex,
			fragmentCount,
			totalLength,
			payload,
			HEADER_LENGTH,
			contentLength
		);
	}

	private static long getNextMessageId(long current) {
		if (current == Long.MAX_VALUE) {
			return 1;
		}
		return current + 1;
	}

	record Fragment(
		long messageId,
		int fragmentIndex,
		int fragmentCount,
		int totalLength,
		byte[] payload,
		int contentOffset,
		int contentLength
	) {
		void copyContentTo(byte[] destination) {
			int destinationOffset = fragmentIndex * MAX_CHUNK_DATA_LENGTH;
			System.arraycopy(payload, contentOffset, destination, destinationOffset, contentLength);
		}
	}
}
