package net.mezzdev.config;

public enum SearchBarPosition {
	STANDARD,
	CENTERED;

	public static SearchBarPosition fromCentered(boolean centered) {
		return centered ? CENTERED : STANDARD;
	}

	public boolean isCentered() {
		return this == CENTERED;
	}
}
