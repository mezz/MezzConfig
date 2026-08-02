package net.mezzdev.config.api.value;

/**
 * Listener for config value changes.
 * <p>
 * Register your listener here: {@link IConfigValue#addListener(IConfigValueChangeListener)}.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface IConfigValueChangeListener<T> {
	/**
	 * Called when a config value changes.
	 *
	 * @param oldValue value before the change
	 * @param newValue value after the change
	 *
	 * @since 0.1.0
	 */
	void onChange(T oldValue, T newValue);
}
