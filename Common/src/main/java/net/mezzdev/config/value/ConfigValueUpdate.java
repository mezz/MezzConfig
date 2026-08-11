package net.mezzdev.config.value;

import org.jetbrains.annotations.Nullable;

public record ConfigValueUpdate<T>(
	ConfigValue<T> configValue,
	T newValue
) {
	public ConfigValueUpdate {
		newValue = configValue.snapshotUpdateValue(newValue);
	}

	public @Nullable AppliedConfigValueChange<T> apply() {
		return configValue.setValidatedValueWithoutNotifying(newValue);
	}
}
