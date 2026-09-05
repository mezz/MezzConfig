package net.mezzdev.config.serializers;

import net.mezzdev.config.api.value.color.ConfigColorFormat;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.color.PackedColor;
import org.jetbrains.annotations.Nullable;

/**
 * Serializer for RGB and ARGB color config values stored as packed integers.
 */
public final class ColorSerializer implements IConfigValueSerializer<PackedColor> {
	/**
	 * Shared packed color serializer instance.
	 */
	public static final ColorSerializer INSTANCE = new ColorSerializer();

	private static final String PREFIX = "0x";
	private static final int RGB_HEX_DIGITS = 6;
	private static final int ARGB_HEX_DIGITS = 8;

	private ColorSerializer() {}

	@Override
	public String serialize(PackedColor value) {
		return switch (value.format()) {
			case RGB -> "0x%06X".formatted(value.packedValue());
			case ARGB -> "0x%08X".formatted(value.packedValue());
		};
	}

	@Override
	public DeserializeResult<PackedColor> deserialize(String string) {
		string = string.trim();
		if (!string.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			return DeserializeResult.failure("Invalid color. Must be: " + getValidValuesDescription());
		}

		String hex = string.substring(PREFIX.length());
		ConfigColorFormat format = switch (hex.length()) {
			case RGB_HEX_DIGITS -> ConfigColorFormat.RGB;
			case ARGB_HEX_DIGITS -> ConfigColorFormat.ARGB;
			default -> null;
		};
		if (format == null) {
			return DeserializeResult.failure("Invalid color. Must be: " + getValidValuesDescription());
		}

		try {
			long unsignedValue = Long.parseUnsignedLong(hex, 16);
			return DeserializeResult.success(new PackedColor((int) unsignedValue, format));
		} catch (NumberFormatException e) {
			String errorMessage = "Unable to parse color: '%s' with error:\n%s".formatted(string, e.getMessage());
			return DeserializeResult.failure(errorMessage);
		}
	}

	@Override
	public boolean isValid(@Nullable PackedColor value) {
		return value != null;
	}

	@Override
	public String getValidValuesDescription() {
		return "An RGB or ARGB color serialized as 0xRRGGBB or 0xAARRGGBB";
	}
}
