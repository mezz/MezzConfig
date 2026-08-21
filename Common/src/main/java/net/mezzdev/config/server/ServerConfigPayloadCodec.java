package net.mezzdev.config.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ServerConfigPayloadCodec {
	static final int MAX_VALUE_COUNT = 4_096;
	static final int MAX_MOD_ID_BYTES = 128;
	static final int MAX_CONFIG_FILE_NAME_BYTES = 512;
	static final int MAX_CATEGORY_NAME_BYTES = 128;
	static final int MAX_VALUE_NAME_BYTES = 128;
	static final int MAX_SERIALIZED_VALUE_BYTES = 256 * 1024;

	private ServerConfigPayloadCodec() {

	}

	static byte[] encodeSync(ServerConfigSyncPayload payload) {
		return encode(output -> {
			writeKey(output, payload.key());
			writeValues(output, payload.values());
		});
	}

	static ServerConfigSyncPayload decodeSync(byte[] data) {
		return decode(data, input -> new ServerConfigSyncPayload(
			readKey(input),
			readValues(input)
		));
	}

	private static byte[] encode(IoConsumer<DataOutputStream> encoder) {
		try {
			ByteArrayOutputStream bytes = new BoundedByteArrayOutputStream(
				ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH
			);
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				encoder.accept(output);
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to encode a server config payload.", e);
		}
	}

	private static <T> T decode(byte[] data, IoFunction<DataInputStream, T> decoder) {
		if (data.length > ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("Server config payload exceeds the maximum length of " +
				ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH + " bytes: " + data.length);
		}
		try {
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
			T result = decoder.apply(input);
			if (input.available() != 0) {
				throw new IllegalArgumentException("Server config payload has trailing data.");
			}
			return result;
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid server config payload.", e);
		}
	}

	private static void writeKey(DataOutputStream output, ServerConfigKey key) throws IOException {
		writeString(output, key.modId(), MAX_MOD_ID_BYTES, "mod id");
		writeString(output, key.configFileName(), MAX_CONFIG_FILE_NAME_BYTES, "config file name");
	}

	private static ServerConfigKey readKey(DataInputStream input) throws IOException {
		return new ServerConfigKey(
			readString(input, MAX_MOD_ID_BYTES, "mod id"),
			readString(input, MAX_CONFIG_FILE_NAME_BYTES, "config file name")
		);
	}

	private static void writeValues(DataOutputStream output, List<ServerConfigValueData> values) throws IOException {
		if (values.size() > MAX_VALUE_COUNT) {
			throw new IllegalArgumentException("Too many server config values: " + values.size());
		}
		output.writeInt(values.size());
		for (ServerConfigValueData value : values) {
			writeString(output, value.categoryName(), MAX_CATEGORY_NAME_BYTES, "category name");
			writeString(output, value.valueName(), MAX_VALUE_NAME_BYTES, "value name");
			writeString(output, value.serializedValue(), MAX_SERIALIZED_VALUE_BYTES, "serialized value");
		}
	}

	private static List<ServerConfigValueData> readValues(DataInputStream input) throws IOException {
		int size = input.readInt();
		if (size < 0 || size > MAX_VALUE_COUNT || size > input.available() / (Integer.BYTES * 3)) {
			throw new IllegalArgumentException("Invalid server config value count: " + size);
		}
		List<ServerConfigValueData> values = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			String categoryName = readString(input, MAX_CATEGORY_NAME_BYTES, "category name");
			String valueName = readString(input, MAX_VALUE_NAME_BYTES, "value name");
			String serializedValue = readString(input, MAX_SERIALIZED_VALUE_BYTES, "serialized value");
			values.add(new ServerConfigValueData(categoryName, valueName, serializedValue));
		}
		return List.copyOf(values);
	}

	private static void writeString(
		DataOutputStream output,
		String value,
		int maxEncodedLength,
		String fieldName
	) throws IOException {
		int encodedLength = getUtf8Length(value, maxEncodedLength, fieldName);
		long payloadLength = (long) output.size() + Integer.BYTES + encodedLength;
		if (payloadLength > ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH) {
			throw payloadTooLarge(payloadLength);
		}
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encodedLength);
		output.write(encoded);
	}

	private static int getUtf8Length(String value, int maxEncodedLength, String fieldName) {
		if (value.length() > maxEncodedLength) {
			throw stringTooLarge(fieldName, maxEncodedLength);
		}
		int encodedLength = 0;
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character <= 0x7F) {
				encodedLength++;
			} else if (character <= 0x7FF) {
				encodedLength += 2;
			} else if (Character.isHighSurrogate(character)) {
				if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
					throw new IllegalArgumentException("Server config " + fieldName + " contains invalid UTF-16.");
				}
				i++;
				encodedLength += 4;
			} else if (Character.isLowSurrogate(character)) {
				throw new IllegalArgumentException("Server config " + fieldName + " contains invalid UTF-16.");
			} else {
				encodedLength += 3;
			}
			if (encodedLength > maxEncodedLength) {
				throw stringTooLarge(fieldName, maxEncodedLength);
			}
		}
		return encodedLength;
	}

	private static IllegalArgumentException stringTooLarge(String fieldName, int maxEncodedLength) {
		return new IllegalArgumentException(
			"Server config " + fieldName + " exceeds " + maxEncodedLength + " UTF-8 bytes."
		);
	}

	private static IllegalArgumentException payloadTooLarge(long length) {
		return new IllegalArgumentException("Server config payload exceeds the maximum length of " +
			ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH + " bytes: " + length);
	}

	private static String readString(DataInputStream input, int maxEncodedLength, String fieldName) throws IOException {
		int length = input.readInt();
		if (length < 0 || length > maxEncodedLength) {
			throw new IllegalArgumentException("Invalid server config " + fieldName + " length: " + length);
		}
		if (length > input.available()) {
			throw new EOFException("Truncated server config " + fieldName + ".");
		}
		byte[] encoded = input.readNBytes(length);
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(encoded))
				.toString();
		} catch (CharacterCodingException e) {
			throw new IllegalArgumentException("Server config payload contains invalid UTF-8.", e);
		}
	}

	@FunctionalInterface
	private interface IoConsumer<T> {
		void accept(T value) throws IOException;
	}

	@FunctionalInterface
	private interface IoFunction<T, R> {
		R apply(T value) throws IOException;
	}

	private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
		private final int maxLength;

		private BoundedByteArrayOutputStream(int maxLength) {
			super(Math.min(32, maxLength));
			this.maxLength = maxLength;
		}

		@Override
		public synchronized void write(int value) {
			ensureCapacityFor(1);
			super.write(value);
		}

		@Override
		public synchronized void write(byte[] data, int offset, int length) {
			ensureCapacityFor(length);
			super.write(data, offset, length);
		}

		private void ensureCapacityFor(int additionalLength) {
			if (additionalLength > maxLength - count) {
				throw payloadTooLarge((long) count + additionalLength);
			}
		}
	}
}
