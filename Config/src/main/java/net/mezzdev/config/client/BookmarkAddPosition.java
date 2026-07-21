package net.mezzdev.config.client;

public enum BookmarkAddPosition {
	END,
	FRONT;

	public boolean isFront() {
		return this == FRONT;
	}
}
