package net.mezzdev.config.neoforge.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;

final class GameTestFailures {
	private GameTestFailures() {}
	static GameTestAssertException create(String message) {
		return new GameTestAssertException(message);
	}
}
