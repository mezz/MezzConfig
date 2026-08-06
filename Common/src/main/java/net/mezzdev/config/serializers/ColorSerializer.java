package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Serializer for ARGB color config values stored as 32-bit integers.
 */
public final class ColorSerializer implements IConfigValueSerializer<Integer> {
	/**
	 * Shared ARGB color serializer instance.
	 */
	public static final ColorSerializer INSTANCE = new ColorSerializer();

	private static final String PREFIX = "0x";
	private static final int HEX_DIGITS = 8;
	private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;

	private ColorSerializer() {}

	@Override
	public String serialize(Integer value) {
		return "0x%08X".formatted(value);
	}

	@Override
	public DeserializeResult<Integer> deserialize(String string) {
		string = string.trim();
		if (string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1);
		}
		if (!string.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			return new DeserializeResult<>(null, "Invalid color. Must be: " + getValidValuesDescription());
		}

		String hex = string.substring(PREFIX.length());
		if (hex.length() != HEX_DIGITS) {
			return new DeserializeResult<>(null, "Invalid color. Must be: " + getValidValuesDescription());
		}

		try {
			long unsignedValue = Long.parseUnsignedLong(hex, 16);
			if (unsignedValue > MAX_UNSIGNED_INT) {
				return new DeserializeResult<>(null, "Invalid color. Must be: " + getValidValuesDescription());
			}
			return new DeserializeResult<>((int) unsignedValue);
		} catch (NumberFormatException e) {
			String errorMessage = "Unable to parse color: '%s' with error:\n%s".formatted(string, e.getMessage());
			return new DeserializeResult<>(null, errorMessage);
		}
	}

	@Override
	public boolean isValid(@Nullable Integer value) {
		return value != null;
	}

	@Override
	public String getValidValuesDescription() {
		return "An ARGB color serialized as 0xAARRGGBB";
	}
}
