package net.mezzdev.config.api.value.color;

import java.util.Objects;

/**
 * A color stored as a packed integer.
 *
 * @param packedValue packed {@code 0xRRGGBB} or {@code 0xAARRGGBB} value
 * @param format format of the packed value
 *
 * @since 0.1.0
 */
public record PackedColor(int packedValue, ConfigColorFormat format) {
	/**
	 * Validate and create a packed color.
	 *
	 * @since 0.1.0
	 */
	public PackedColor {
		Objects.requireNonNull(format);
		if (format == ConfigColorFormat.RGB && (packedValue & 0xFF00_0000) != 0) {
			throw new IllegalArgumentException("RGB colors must be packed as 0xRRGGBB");
		}
	}

	/**
	 * Create an RGB color stored as {@code 0xRRGGBB}.
	 *
	 * @param packedValue packed RGB value
	 * @return the packed color
	 *
	 * @since 0.1.0
	 */
	public static PackedColor rgb(int packedValue) {
		return new PackedColor(packedValue, ConfigColorFormat.RGB);
	}

	/**
	 * Create an ARGB color stored as {@code 0xAARRGGBB}.
	 *
	 * @param packedValue packed ARGB value
	 * @return the packed color
	 *
	 * @since 0.1.0
	 */
	public static PackedColor argb(int packedValue) {
		return new PackedColor(packedValue, ConfigColorFormat.ARGB);
	}
}
