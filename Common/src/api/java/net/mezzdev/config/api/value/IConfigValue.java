package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.builder.IConfigValueBuilder;
import net.mezzdev.config.api.value.change.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.change.IConfigValueChangeListener;
import net.mezzdev.config.api.value.editor.IConfigValueEditorInfo;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * Provides runtime access to one config setting.
 * <p>
 * Keep the instance returned by {@link IConfigValueBuilder#build()}. Use {@link #get()} for the setting currently in effect
 * and {@link #set(Object)} to validate, save, and apply a new value. For settings that require a restart,
 * {@link IConfigValueEditorInfo#getPendingValue()} reports what is saved for the next restart while {@code get()}
 * continues to report the active value. Config editors can inspect saved state, identity, and metadata through
 * {@link #getEditorInfo()}.
 * <p>
 * Runtime methods and listener registration are thread-safe. Listeners run synchronously after a change is applied.
 * Listener registrations normally live as long as this config value, which is usually the full mod lifetime, so callers
 * may ignore their returned removal callbacks. Keep and run a removal callback when its listener captures a shorter-lived
 * object, such as a screen, reloadable runtime, or connection-specific component. Client-per-world values retain their
 * listeners across world changes and notify them when the effective value changes.
 *
 * @param <T> an effectively immutable value type with stable {@link Object#equals(Object)} behavior
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValue<T> {
	/**
	 * Get the value currently in effect.
	 * <p>
	 * Values are immutable by contract. Built-in list values return an unmodifiable snapshot.
	 *
	 * @since 0.1.0
	 */
	T get();

	/**
	 * Validate and save a new value.
	 * <p>
	 * The value becomes effective immediately unless this setting has a restart requirement. Use
	 * {@link IConfigSchema#batchUpdate(Consumer)} when several settings must change together.
	 *
	 * @param value new value
	 * @return {@code true} if the saved value changed, or {@code false} if it was valid but already pending
	 *
	 * @throws IllegalArgumentException if the value is invalid or cannot be safely serialized
	 * @throws IllegalStateException if this value's schema has no active local backing file, including synchronized server
	 * values viewed on a remote client
	 *
	 * @since 0.1.0
	 */
	boolean set(T value);

	/**
	 * Run code when this setting's {@link #get()} changes.
	 * <p>
	 * Use this to refresh behavior that depends on the active value. A restart-required edit invokes this listener only when
	 * the saved value becomes effective.
	 * @param listener callback accepting the applied change
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addListener(IConfigValueChangeListener<T> listener);

	/**
	 * Observe the complete effective-value batch whenever it includes this setting.
	 * <p>
	 * Register the same listener on every setting a reaction depends on. When several of those settings change together, the
	 * listener runs once with the complete batch.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get the saved state, identity, metadata, and listener hooks used by config editors.
	 *
	 * @since 0.3.0
	 */
	IConfigValueEditorInfo<T> getEditorInfo();
}
