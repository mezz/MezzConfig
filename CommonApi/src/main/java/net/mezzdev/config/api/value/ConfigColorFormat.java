package net.mezzdev.config.api.value;

/**
 * Formats used by {@link PackedColor}.
 *
 * @since 0.1.0
 */
public enum ConfigColorFormat {
	/**
	 * An RGB color stored as {@code 0xRRGGBB} without an alpha channel.
	 *
	 * @since 0.1.0
	 */
	RGB,

	/**
	 * An ARGB color stored as {@code 0xAARRGGBB} with an alpha channel.
	 *
	 * @since 0.1.0
	 */
	ARGB
}
