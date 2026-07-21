package net.mezzdev.config;

/**
 * Combined horizontal and vertical alignment for display in config screens.
 */
public enum ConfigAlignment {
	TOP_LEFT(HorizontalConfigAlignment.LEFT, VerticalConfigAlignment.TOP, 0, 0),
	TOP_CENTER(HorizontalConfigAlignment.CENTER, VerticalConfigAlignment.TOP, 1, 0),
	TOP_RIGHT(HorizontalConfigAlignment.RIGHT, VerticalConfigAlignment.TOP, 2, 0),
	CENTER_LEFT(HorizontalConfigAlignment.LEFT, VerticalConfigAlignment.CENTER, 0, 1),
	CENTER(HorizontalConfigAlignment.CENTER, VerticalConfigAlignment.CENTER, 1, 1),
	CENTER_RIGHT(HorizontalConfigAlignment.RIGHT, VerticalConfigAlignment.CENTER, 2, 1),
	BOTTOM_LEFT(HorizontalConfigAlignment.LEFT, VerticalConfigAlignment.BOTTOM, 0, 2),
	BOTTOM_CENTER(HorizontalConfigAlignment.CENTER, VerticalConfigAlignment.BOTTOM, 1, 2),
	BOTTOM_RIGHT(HorizontalConfigAlignment.RIGHT, VerticalConfigAlignment.BOTTOM, 2, 2);

	private final HorizontalConfigAlignment horizontalAlignment;
	private final VerticalConfigAlignment verticalAlignment;
	private final int column;
	private final int row;

	ConfigAlignment(HorizontalConfigAlignment horizontalAlignment, VerticalConfigAlignment verticalAlignment, int column, int row) {
		this.horizontalAlignment = horizontalAlignment;
		this.verticalAlignment = verticalAlignment;
		this.column = column;
		this.row = row;
	}

	public HorizontalConfigAlignment horizontalAlignment() {
		return horizontalAlignment;
	}

	public VerticalConfigAlignment verticalAlignment() {
		return verticalAlignment;
	}

	public int column() {
		return column;
	}

	public int row() {
		return row;
	}

	public static ConfigAlignment from(HorizontalConfigAlignment horizontalAlignment, VerticalConfigAlignment verticalAlignment) {
		for (ConfigAlignment alignment : values()) {
			if (alignment.horizontalAlignment == horizontalAlignment && alignment.verticalAlignment == verticalAlignment) {
				return alignment;
			}
		}
		return CENTER;
	}
}
