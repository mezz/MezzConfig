package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IPendingConfigValueUpdate;
import org.jetbrains.annotations.Nullable;

public record PendingConfigValueUpdate<T>(
	ConfigValue<T> configValue,
	T newValue
) implements IPendingConfigValueUpdate<T> {
	public void validate() {
		configValue.validateUpdateValue(newValue);
	}

	public @Nullable AppliedConfigValueChange<T> apply() {
		return configValue.setWithoutNotifying(newValue);
	}
}
