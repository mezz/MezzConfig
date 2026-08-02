package net.mezzdev.config.value;

import net.mezzdev.config.api.value.IAppliedConfigValueChange;

public record AppliedConfigValueChange<T>(
	ConfigValue<T> configValue,
	T oldValue,
	T newValue
) implements IAppliedConfigValueChange<T> {}
