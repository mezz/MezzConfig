package net.mezzdev.config.server;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

final class ServerConfigPayloadReassembler {
	static final int MAX_CONCURRENT_MESSAGES = 4;
	static final int MAX_PENDING_BYTES = 2 * ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH;
	static final Duration INCOMPLETE_MESSAGE_TIMEOUT = Duration.ofSeconds(10);
	private static final long INCOMPLETE_MESSAGE_TIMEOUT_NANOS = INCOMPLETE_MESSAGE_TIMEOUT.toNanos();

	private final Map<Long, Assembly> assemblies = new HashMap<>();
	private int pendingBytes;

	public synchronized Optional<byte[]> accept(byte[] payload) {
		return accept(payload, System.nanoTime());
	}

	synchronized Optional<byte[]> accept(byte[] payload, long nowNanos) {
		expire(nowNanos);
		ServerConfigPayloadChunker.Fragment fragment = ServerConfigPayloadChunker.parse(payload);
		Assembly assembly = assemblies.get(fragment.messageId());
		if (assembly == null) {
			if (assemblies.size() >= MAX_CONCURRENT_MESSAGES) {
				throw new IllegalArgumentException("Too many incomplete server config messages.");
			}
			if (fragment.totalLength() > MAX_PENDING_BYTES - pendingBytes) {
				throw new IllegalArgumentException("Incomplete server config messages exceed the pending byte limit.");
			}
			assembly = new Assembly(fragment, nowNanos);
			assemblies.put(fragment.messageId(), assembly);
			pendingBytes += fragment.totalLength();
		} else if (!assembly.metadataMatches(fragment)) {
			remove(fragment.messageId());
			throw new IllegalArgumentException("Server config fragments with the same message id have inconsistent metadata.");
		}

		try {
			if (!assembly.accept(fragment)) {
				return Optional.empty();
			}
			byte[] result = assembly.data();
			remove(fragment.messageId());
			return Optional.of(result);
		} catch (RuntimeException e) {
			remove(fragment.messageId());
			throw e;
		}
	}

	public synchronized void clear() {
		assemblies.clear();
		pendingBytes = 0;
	}

	synchronized int expire(long nowNanos) {
		int expired = 0;
		Iterator<Map.Entry<Long, Assembly>> iterator = assemblies.entrySet().iterator();
		while (iterator.hasNext()) {
			Assembly assembly = iterator.next().getValue();
			if (nowNanos - assembly.createdNanos() >= INCOMPLETE_MESSAGE_TIMEOUT_NANOS) {
				pendingBytes -= assembly.data().length;
				iterator.remove();
				expired++;
			}
		}
		return expired;
	}

	synchronized Optional<Duration> getTimeUntilNextExpiration(long nowNanos) {
		return assemblies.values()
			.stream()
			.mapToLong(Assembly::createdNanos)
			.min()
			.stream()
			.mapToObj(createdNanos -> {
				long elapsedNanos = nowNanos - createdNanos;
				long remainingNanos = Math.max(0, INCOMPLETE_MESSAGE_TIMEOUT_NANOS - elapsedNanos);
				return Duration.ofNanos(remainingNanos);
			})
			.findFirst();
	}

	synchronized boolean isEmpty() {
		return assemblies.isEmpty();
	}

	synchronized int pendingMessageCount() {
		return assemblies.size();
	}

	private void remove(long messageId) {
		Assembly removed = assemblies.remove(messageId);
		if (removed != null) {
			pendingBytes -= removed.data().length;
		}
	}

	private static final class Assembly {
		private final int fragmentCount;
		private final long createdNanos;
		private final byte[] data;
		private final boolean[] receivedFragments;
		private int receivedCount;

		private Assembly(ServerConfigPayloadChunker.Fragment firstFragment, long createdNanos) {
			this.fragmentCount = firstFragment.fragmentCount();
			this.createdNanos = createdNanos;
			this.data = new byte[firstFragment.totalLength()];
			this.receivedFragments = new boolean[fragmentCount];
		}

		private boolean metadataMatches(ServerConfigPayloadChunker.Fragment fragment) {
			return fragment.fragmentCount() == fragmentCount && fragment.totalLength() == data.length;
		}

		private boolean accept(ServerConfigPayloadChunker.Fragment fragment) {
			int fragmentIndex = fragment.fragmentIndex();
			if (receivedFragments[fragmentIndex]) {
				throw new IllegalArgumentException("Received a duplicate server config fragment: " + fragmentIndex);
			}
			fragment.copyContentTo(data);
			receivedFragments[fragmentIndex] = true;
			receivedCount++;
			return receivedCount == fragmentCount;
		}

		private long createdNanos() {
			return createdNanos;
		}

		private byte[] data() {
			return data;
		}
	}
}
