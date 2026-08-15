package net.mezzdev.config.api.value;

/**
 * Listener for effective config value changes.
 * <p>
 * Register your listener here: {@link IConfigValue#addListener(IConfigValueChangeListener)}.
 * The registration method returns a callback that unsubscribes the listener.
 * <p>
 * Callbacks run synchronously on the thread applying the change; MezzConfig does not dispatch them to another thread.
 * API updates use the thread calling {@link IConfigValue#set(Object)}, while file-backed updates use the thread that
 * next loads the changed schema. A runtime exception from one callback is logged and does not prevent persistence or
 * later callbacks.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface IConfigValueChangeListener<T> {
	/**
	 * Called when a config value becomes effective.
	 *
	 * @param change the applied change
	 *
	 * @since 0.1.0
	 */
	void onChange(IAppliedConfigValueChange<T> change);
}
