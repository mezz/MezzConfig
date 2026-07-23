package mezz.jei.common.config;

import net.mezzdev.config.value.ConfigValueUpdateType;
import net.mezzdev.config.schema.IConfigCategoryBuilder;
import net.mezzdev.config.schema.IConfigSchemaBuilder;
import net.mezzdev.config.value.IConfigValue;
import org.jetbrains.annotations.Nullable;

public final class DebugConfig {
	@Nullable
	private static DebugConfig instance;

	public static void create(IConfigSchemaBuilder schema) {
		instance = new DebugConfig(schema);
	}

	private final IConfigValue<Boolean> debugModeEnabled;
	private final IConfigValue<Boolean> debugGuisEnabled;
	private final IConfigValue<Boolean> debugInputsEnabled;
	private final IConfigValue<Boolean> debugInfoTooltipsEnabled;
	private final IConfigValue<Boolean> crashingTestIngredientsEnabled;
	private final IConfigValue<Boolean> crashingTestRecipesEnabled;
	private final IConfigValue<Boolean> logSuffixTreeStats;

	private DebugConfig(IConfigSchemaBuilder schema) {
		IConfigCategoryBuilder advanced = schema.addCategory("debug");
		debugModeEnabled = advanced.addBoolean("debugMode", false, ConfigValueUpdateType.RESTART);
		debugGuisEnabled = advanced.addBoolean("debugGuis", false, ConfigValueUpdateType.IMMEDIATE);
		debugInputsEnabled = advanced.addBoolean("debugInputs", false, ConfigValueUpdateType.IMMEDIATE);
		debugInfoTooltipsEnabled = advanced.addBoolean("debugInfoTooltipsEnabled", false, ConfigValueUpdateType.IMMEDIATE);
		crashingTestIngredientsEnabled = advanced.addBoolean("crashingTestItemsEnabled", false, ConfigValueUpdateType.RESTART);
		crashingTestRecipesEnabled =  advanced.addBoolean("crashingTestRecipesEnabled", false, ConfigValueUpdateType.RESTART);
		logSuffixTreeStats = advanced.addBoolean("logSuffixTreeStats", false, ConfigValueUpdateType.RESTART);
	}

	public static boolean isDebugModeEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugModeEnabled.getValue();
	}

	public static boolean isDebugGuisEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugGuisEnabled.getValue();
	}

	public static boolean isDebugInputsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInputsEnabled.getValue();
	}

	public static boolean isDebugInfoTooltipsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInfoTooltipsEnabled.getValue();
	}

	public static boolean isCrashingTestIngredientsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.crashingTestIngredientsEnabled.getValue();
	}

	public static boolean isCrashingTestRecipesEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.crashingTestRecipesEnabled.getValue();
	}

	public static boolean isLogSuffixTreeStatsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.logSuffixTreeStats.getValue();
	}
}
