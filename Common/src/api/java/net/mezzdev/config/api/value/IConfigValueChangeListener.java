package net.mezzdev.config.api.value;

/**
 * Reacts when one config value changes.
 *
 * @param <T> config value type
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface IConfigValueChangeListener<T> {
	/**
	 * Called after the change has been applied.
	 *
	 * @param change applied config value change
	 *
	 * @since 0.3.0
	 */
	void onConfigValueChanged(IAppliedConfigValueChange<T> change);
}
