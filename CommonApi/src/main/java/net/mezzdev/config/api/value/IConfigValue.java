package net.mezzdev.config.api.value;

import net.mezzdev.config.api.schema.IConfigCategory;
import net.mezzdev.config.api.schema.IConfigCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.api.schema.IConfigSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a config value.
 * Config values can be read or updated by mods.
 * <p>
 * Config values are automatically synchronized with their backing file. A restart-required value distinguishes the
 * value currently in effect from the saved value selected for the next applicable restart.
 * <p>
 * Add a value to your category with the add methods on {@link IConfigCategoryBuilder}.
 * Get registered values here: {@link IConfigCategory#getConfigValues()}.
 * Listener registration and removal are thread-safe, and listener callbacks follow the synchronous ordering, failure
 * isolation, stable-snapshot, and reentrancy contract documented by {@link IConfigSchema}.
 * <p>
 * Runtime methods are thread-safe. Listener behavior follows {@link IConfigSchema}.
 *
 * @param <T> an effectively immutable value type with stable {@link Object#equals(Object)} behavior
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigValue<T> {
	/**
	 * Get the name of this config value.
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
	 * Get the saved value selected by the most recent edit or file load.
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
	 * Set the config value to the given value.
	 * This automatically saves the new value. If a restart is required, the new value remains pending until that lifecycle
	 * boundary and {@link #getValue()} remains unchanged.
	 * Built-in list values are copied to an unmodifiable snapshot before this method returns.
	 * <p>
	 * Use {@link IConfigSchema#batchUpdate(Consumer)} to update
	 * several config values together.
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
	 * Add a listener that is called when this config value's effective value changes. Pending changes do not invoke this
	 * listener. It runs once for this value before its value-scoped batch listeners and the schema batch listeners.
	 * @param listener callback accepting the applied change
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addListener(IConfigValueChangeListener<T> listener);

	/**
	 * Add a listener that is called when this config value's pending saved value changes.
	 * <p>
	 * Values without a restart requirement invoke both effective and pending listeners. Restart-required values invoke
	 * pending listeners when saved and effective listeners later when the applicable restart promotes the value. It runs
	 * once for this value before its pending value-scoped and schema batch listeners.
	 *
	 * @param listener callback accepting the pending change
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingListener(IConfigValueChangeListener<T> listener);

	/**
	 * Add a listener that is called with all effective-value changes from a batch containing this config value. Pending
	 * changes do not invoke this listener.
	 * <p>
	 * Use this when the listener needs to observe other config values updated in the same batch. It runs exactly once when
	 * this value participates in a non-empty effective batch, after this value's single-value listeners and before schema
	 * batch listeners. A batch that changes only other values does not invoke it.
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Add a listener that is called with all pending-value changes from a batch containing this config value.
	 * <p>
	 * Use this when the listener needs to observe other pending values updated in the same batch. It runs exactly once when
	 * this value participates in a non-empty pending batch, after this value's single-value listeners and before schema
	 * pending batch listeners. A batch that changes only other values does not invoke it.
	 *
	 * @param listener callback accepting the pending changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get the helper for serializing values to and from Strings, and validating values.
	 *
	 * @since 0.1.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
