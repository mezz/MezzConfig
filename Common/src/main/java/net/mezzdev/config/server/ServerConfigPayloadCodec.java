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
	private ServerConfigPayloadCodec() {

	}

	static byte[] encodeSync(ServerConfigSyncPayload payload) {
		return encode(output -> {
			writeKey(output, payload.key());
			output.writeLong(payload.requestId());
			output.writeBoolean(payload.accepted());
			output.writeBoolean(payload.canEdit());
			writeString(output, payload.errorMessage());
			writeValues(output, payload.values());
		});
	}

	static ServerConfigSyncPayload decodeSync(byte[] data) {
		return decode(data, input -> new ServerConfigSyncPayload(
			readKey(input),
			input.readLong(),
			input.readBoolean(),
			input.readBoolean(),
			readString(input),
			readValues(input)
		));
	}

	static byte[] encodeUpdate(ServerConfigUpdatePayload payload) {
		return encode(output -> {
			writeKey(output, payload.key());
			output.writeLong(payload.requestId());
			writeValues(output, payload.values());
		});
	}

	static ServerConfigUpdatePayload decodeUpdate(byte[] data) {
		return decode(data, input -> new ServerConfigUpdatePayload(
			readKey(input),
			input.readLong(),
			readValues(input)
		));
	}

	private static byte[] encode(IoConsumer<DataOutputStream> encoder) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				encoder.accept(output);
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to encode a server config payload.", e);
		}
	}

	private static <T> T decode(byte[] data, IoFunction<DataInputStream, T> decoder) {
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
		writeString(output, key.modId());
		writeString(output, key.configFileName());
	}

	private static ServerConfigKey readKey(DataInputStream input) throws IOException {
		return new ServerConfigKey(readString(input), readString(input));
	}

	private static void writeValues(DataOutputStream output, List<ServerConfigValueData> values) throws IOException {
		output.writeInt(values.size());
		for (ServerConfigValueData value : values) {
			writeString(output, value.categoryName());
			writeString(output, value.valueName());
			writeString(output, value.serializedValue());
		}
	}

	private static List<ServerConfigValueData> readValues(DataInputStream input) throws IOException {
		int size = input.readInt();
		if (size < 0 || size > input.available() / (Integer.BYTES * 3)) {
			throw new IllegalArgumentException("Invalid server config value count: " + size);
		}
		List<ServerConfigValueData> values = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			values.add(new ServerConfigValueData(
				readString(input),
				readString(input),
				readString(input)
			));
		}
		return List.copyOf(values);
	}

	private static void writeString(DataOutputStream output, String value) throws IOException {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}

	private static String readString(DataInputStream input) throws IOException {
		int length = input.readInt();
		if (length < 0 || length > input.available()) {
			throw new EOFException("Invalid server config string length: " + length);
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
