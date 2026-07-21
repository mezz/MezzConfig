package net.mezzdev.config;

public enum BookmarkAddPosition {
	END,
	FRONT;

	public boolean isFront() {
		return this == FRONT;
	}
}
