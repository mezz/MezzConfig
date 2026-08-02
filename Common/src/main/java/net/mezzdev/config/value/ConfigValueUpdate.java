package net.mezzdev.config.value;

import org.jetbrains.annotations.Nullable;

public record ConfigValueUpdate<T>(
	ConfigValue<T> configValue,
	T newValue
) {
	public void validate() {
		configValue.validateUpdateValue(newValue);
	}

	public @Nullable AppliedConfigValueChange<T> apply() {
		return configValue.setWithoutNotifying(newValue);
	}
}
