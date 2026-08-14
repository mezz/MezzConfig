package net.mezzdev.config.ini;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class IniFileReader {
	public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
	public static final int MAX_FILE_LINES = 100_000;

	private IniFileReader() {}

	public static Contents read(Path path) throws IOException, MalformedFileException {
		BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
		if (attributes.size() > MAX_FILE_BYTES) {
			throw new MalformedFileException(
				"file exceeds the maximum supported size of %s bytes (actual size: %s)"
					.formatted(MAX_FILE_BYTES, attributes.size()),
				"size:%s;modified:%s".formatted(attributes.size(), attributes.lastModifiedTime().toMillis())
			);
		}
		byte[] bytes;
		try (InputStream input = Files.newInputStream(path)) {
			bytes = input.readNBytes(MAX_FILE_BYTES + 1);
		}
		String fingerprint = hash(bytes);
		if (bytes.length > MAX_FILE_BYTES) {
			throw new MalformedFileException(
				"file grew beyond the maximum supported size of " + MAX_FILE_BYTES + " bytes while it was read",
				fingerprint
			);
		}
		String decoded;
		try {
			decoded = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		} catch (CharacterCodingException e) {
			throw new MalformedFileException("file is not valid UTF-8", fingerprint);
		}
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new StringReader(decoded))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (lines.size() >= MAX_FILE_LINES) {
					throw new MalformedFileException(
						"file exceeds the maximum supported line count of " + MAX_FILE_LINES,
						fingerprint
					);
				}
				lines.add(line);
			}
		}
		return new Contents(lines, fingerprint);
	}

	private static String hash(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available.", e);
		}
	}

	public record Contents(List<String> lines, String fingerprint) {
		public Contents {
			lines = List.copyOf(lines);
		}
	}

	public static final class MalformedFileException extends Exception {
		private final String fingerprint;

		private MalformedFileException(String message, String fingerprint) {
			super(message);
			this.fingerprint = fingerprint;
		}

		public String fingerprint() {
			return fingerprint;
		}
	}
}
