package net.mezzdev.config;

import net.mezzdev.config.file.IConfigCategoryBuilder;
import net.mezzdev.config.file.IConfigSchemaBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class DebugConfig {
	@Nullable
	private static DebugConfig instance;

	public static void create(IConfigSchemaBuilder schema) {
		instance = new DebugConfig(schema);
	}

	private final Supplier<Boolean> debugModeEnabled;
	private final Supplier<Boolean> debugGuisEnabled;
	private final Supplier<Boolean> debugInputsEnabled;
	private final Supplier<Boolean> debugInfoTooltipsEnabled;
	private final Supplier<Boolean> crashingTestIngredientsEnabled;
	private final Supplier<Boolean> crashingTestRecipesEnabled;
	private final Supplier<Boolean> logSuffixTreeStats;

	private DebugConfig(IConfigSchemaBuilder schema) {
		IConfigCategoryBuilder advanced = schema.addCategory("debug");
		debugModeEnabled = advanced.addBoolean("debugMode", false, ConfigValueUpdateType.RESTART_JEI);
		debugGuisEnabled = advanced.addBoolean("debugGuis", false, ConfigValueUpdateType.IMMEDIATE);
		debugInputsEnabled = advanced.addBoolean("debugInputs", false, ConfigValueUpdateType.IMMEDIATE);
		debugInfoTooltipsEnabled = advanced.addBoolean("debugInfoTooltipsEnabled", false, ConfigValueUpdateType.IMMEDIATE);
		crashingTestIngredientsEnabled = advanced.addBoolean("crashingTestItemsEnabled", false, ConfigValueUpdateType.RESTART_JEI);
		crashingTestRecipesEnabled =  advanced.addBoolean("crashingTestRecipesEnabled", false, ConfigValueUpdateType.RESTART_JEI);
		logSuffixTreeStats = advanced.addBoolean("logSuffixTreeStats", false, ConfigValueUpdateType.RESTART_JEI);
	}

	public static boolean isDebugModeEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugModeEnabled.get();
	}

	public static boolean isDebugGuisEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugGuisEnabled.get();
	}

	public static boolean isDebugInputsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInputsEnabled.get();
	}

	public static boolean isDebugInfoTooltipsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInfoTooltipsEnabled.get();
	}

	public static boolean isCrashingTestIngredientsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.crashingTestIngredientsEnabled.get();
	}

	public static boolean isCrashingTestRecipesEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.crashingTestRecipesEnabled.get();
	}

	public static boolean isLogSuffixTreeStatsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.logSuffixTreeStats.get();
	}
}
