package net.mezzdev.config.alignment;

/**
 * Combined horizontal and vertical alignment for display in config screens.
 *
 * @since 19.39.0
 */
public enum Alignment {
	TOP_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.TOP, 0, 0),
	TOP_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.TOP, 1, 0),
	TOP_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.TOP, 2, 0),
	CENTER_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.CENTER, 0, 1),
	CENTER(HorizontalAlignment.CENTER, VerticalAlignment.CENTER, 1, 1),
	CENTER_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, 2, 1),
	BOTTOM_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM, 0, 2),
	BOTTOM_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM, 1, 2),
	BOTTOM_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM, 2, 2);

	private final HorizontalAlignment horizontalAlignment;
	private final VerticalAlignment verticalAlignment;
	private final int column;
	private final int row;

	Alignment(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment, int column, int row) {
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.column = column;
		this.row = row;
	}

	/**
	 * The horizontal part of this alignment.
	 *
	 * @since 19.39.0
	 */
	public HorizontalAlignment horizontalAlignment() {
		return horizontalAlignment;
	}

	/**
	 * The vertical part of this alignment.
	 *
	 * @since 19.39.0
	 */
	public VerticalAlignment verticalAlignment() {
		return verticalAlignment;
	}

	/**
	 * The zero-based column used when drawing this alignment in a 3 by 3 selector.
	 *
	 * @since 19.39.0
	 */
	public int column() {
		return column;
	}

	/**
	 * The zero-based row used when drawing this alignment in a 3 by 3 selector.
	 *
	 * @since 19.39.0
	 */
	public int row() {
		return row;
	}

	/**
	 * Get the combined alignment for the given horizontal and vertical values.
	 *
	 * @since 19.39.0
	 */
	public static Alignment from(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
		for (Alignment alignment : values()) {
			if (alignment.horizontalAlignment == horizontalAlignment && alignment.verticalAlignment == verticalAlignment) {
				return alignment;
			}
		}
		return CENTER;
	}
}
