package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provides runtime access to one config setting.
 * <p>
 * Keep the instance returned by {@link IConfigValueBuilder#build()}. Use {@link #getValue()} for the setting currently in
 * effect and {@link #set(Object)} to validate, save, and apply a new value. For settings that require a restart,
 * {@link #getPendingValue()} reports what is saved for the next restart while {@code getValue()} continues to report the
 * active value.
 * <p>
 * Runtime methods and listener registration are thread-safe. Listeners run synchronously after a change is applied.
 *
 * @param <T> an effectively immutable value type with stable {@link Object#equals(Object)} behavior
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValue<T> {
	/**
	 * Get the stable storage name of this config value.
	 *
	 * @since 0.1.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config value's name.
	 *
	 * @since 0.1.0
	 */
	String getLocalizationKey();

	/**
	 * Get the value currently in effect.
	 * <p>
	 * Values are immutable by contract. Built-in list values return an unmodifiable snapshot.
	 *
	 * @since 0.1.0
	 */
	T getValue();

	/**
	 * Get the saved value, including a restart-required change that is not effective yet.
	 * <p>
	 * This equals {@link #getValue()} when no restart is required or no change is pending. Setting this back to the
	 * effective value cancels a pending change. Synchronized remote server schemas replicate effective values only, so
	 * their pending value always equals their effective value.
	 *
	 * @since 0.3.0
	 */
	T getPendingValue();

	/**
	 * Get the default value.
	 * <p>
	 * Values are immutable by contract. Built-in list values return an unmodifiable snapshot.
	 *
	 * @since 0.1.0
	 */
	T getDefaultValue();

	/**
	 * Get the edit mode hint for this value.
	 * <p>
	 * Config editors can use this to decide when changes should be saved.
	 *
	 * @since 0.1.0
	 */
	ConfigValueEditMode getEditMode();

	/**
	 * Get the lifecycle boundary when saved changes become effective.
	 *
	 * @since 0.1.0
	 */
	ConfigValueRestartRequirement getRestartRequirement();

	/**
	 * Get the categories where config editors should show this value.
	 * <p>
	 * If this is empty, config editors can show the value in the category that contains it.
	 * Values may be shown in multiple editor categories. Categories are returned in editor category order from
	 * {@link IConfigSchema#getEditorCategories()}.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigEditorCategory> getEditorCategories();

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
	 * Run code when this setting's effective value changes.
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
	 * Run code when this setting's saved value changes, even if it is waiting for a restart.
	 * <p>
	 * Use this for editors or diagnostics that display the saved selection.
	 *
	 * @param listener callback accepting the pending change
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingListener(IConfigValueChangeListener<T> listener);

	/**
	 * Observe the complete effective-value batch whenever it includes this setting.
	 * <p>
	 * Use this when reacting correctly requires the other settings changed by the same operation.
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Observe the complete saved-value batch whenever it includes this setting.
	 * <p>
	 * Use this when an editor or diagnostic view needs all saved selections from the same operation.
	 *
	 * @param listener callback accepting the pending changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get this setting's serializer for validation or custom editor integration.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
