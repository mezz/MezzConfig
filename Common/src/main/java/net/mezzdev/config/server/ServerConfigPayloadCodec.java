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
	static final int MAX_ERROR_MESSAGE_BYTES = 4 * 1024;

	private ServerConfigPayloadCodec() {

	}

	static byte[] encodeSync(ServerConfigSyncPayload payload) {
		return encode(output -> {
			writeKey(output, payload.key());
			output.writeLong(payload.requestId());
			writeBoolean(output, payload.accepted());
			writeBoolean(output, payload.canEdit());
			writeString(output, payload.errorMessage(), MAX_ERROR_MESSAGE_BYTES, "error message");
			writeValues(output, payload.values(), true);
		});
	}

	static ServerConfigSyncPayload decodeSync(byte[] data) {
		return decode(data, input -> new ServerConfigSyncPayload(
			readKey(input),
			input.readLong(),
			readBoolean(input, "accepted"),
			readBoolean(input, "canEdit"),
			readString(input, MAX_ERROR_MESSAGE_BYTES, "error message"),
			readValues(input, true)
		));
	}

	static byte[] encodeUpdate(ServerConfigUpdatePayload payload) {
		return encode(output -> {
			writeKey(output, payload.key());
			output.writeLong(payload.requestId());
			writeValues(output, payload.values(), false);
		});
	}

	static ServerConfigUpdatePayload decodeUpdate(byte[] data) {
		return decode(data, input -> new ServerConfigUpdatePayload(
			readKey(input),
			input.readLong(),
			readValues(input, false)
		));
	}

	private static byte[] encode(IoConsumer<DataOutputStream> encoder) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				encoder.accept(output);
			}
			byte[] encoded = bytes.toByteArray();
			if (encoded.length > ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH) {
				throw new IllegalArgumentException("Server config payload exceeds the maximum length of " +
					ServerConfigPayloadChunker.MAX_REASSEMBLED_PAYLOAD_LENGTH + " bytes: " + encoded.length);
			}
			return encoded;
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

	private static void writeValues(
		DataOutputStream output,
		List<ServerConfigValueData> values,
		boolean includeEffectiveValues
	) throws IOException {
		if (values.size() > MAX_VALUE_COUNT) {
			throw new IllegalArgumentException("Too many server config values: " + values.size());
		}
		output.writeInt(values.size());
		for (ServerConfigValueData value : values) {
			writeString(output, value.categoryName(), MAX_CATEGORY_NAME_BYTES, "category name");
			writeString(output, value.valueName(), MAX_VALUE_NAME_BYTES, "value name");
			if (includeEffectiveValues) {
				writeString(output, value.serializedEffectiveValue(), MAX_SERIALIZED_VALUE_BYTES, "serialized effective value");
			}
			writeString(output, value.serializedPendingValue(), MAX_SERIALIZED_VALUE_BYTES, "serialized pending value");
		}
	}

	private static List<ServerConfigValueData> readValues(DataInputStream input, boolean includeEffectiveValues) throws IOException {
		int size = input.readInt();
		if (size < 0 || size > MAX_VALUE_COUNT || size > input.available() / (Integer.BYTES * 3)) {
			throw new IllegalArgumentException("Invalid server config value count: " + size);
		}
		List<ServerConfigValueData> values = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			String categoryName = readString(input, MAX_CATEGORY_NAME_BYTES, "category name");
			String valueName = readString(input, MAX_VALUE_NAME_BYTES, "value name");
			if (includeEffectiveValues) {
				String effectiveValue = readString(input, MAX_SERIALIZED_VALUE_BYTES, "serialized effective value");
				String pendingValue = readString(input, MAX_SERIALIZED_VALUE_BYTES, "serialized pending value");
				values.add(new ServerConfigValueData(categoryName, valueName, effectiveValue, pendingValue));
			} else {
				String pendingValue = readString(input, MAX_SERIALIZED_VALUE_BYTES, "serialized pending value");
				values.add(new ServerConfigValueData(categoryName, valueName, pendingValue));
			}
		}
		return List.copyOf(values);
	}

	private static void writeBoolean(DataOutputStream output, boolean value) throws IOException {
		if (value) {
			output.writeByte(1);
		} else {
			output.writeByte(0);
		}
	}

	private static boolean readBoolean(DataInputStream input, String fieldName) throws IOException {
		int value = input.readUnsignedByte();
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException("Invalid server config boolean for " + fieldName + ": " + value);
		}
		return value == 1;
	}

	private static void writeString(
		DataOutputStream output,
		String value,
		int maxEncodedLength,
		String fieldName
	) throws IOException {
		if (value.length() > maxEncodedLength) {
			throw new IllegalArgumentException("Server config " + fieldName + " is too long.");
		}
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > maxEncodedLength) {
			throw new IllegalArgumentException("Server config " + fieldName + " exceeds " + maxEncodedLength + " UTF-8 bytes.");
		}
		output.writeInt(encoded.length);
		output.write(encoded);
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
}
