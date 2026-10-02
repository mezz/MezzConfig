package net.mezzdev.config.value;

public interface ConfigValueOwner {
	<T> T getEffectiveValue(ConfigValue<T> value);
	<T> T getPendingValue(ConfigValue<T> value);
	<T> boolean setValue(ConfigValue<T> value, T newValue);
	void markDirty();
}
