package net.mezzdev.config.api.value.editor;

import net.mezzdev.config.api.schema.category.IConfigEditorCategory;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.change.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.change.IConfigValueChangeListener;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Provides the saved state, identity, presentation metadata, and listener hooks needed by config editors.
 * <p>
 * Get this from {@link IConfigValue#getEditorInfo()}. Regular feature code should normally use the value methods on
 * {@link IConfigValue}.
 *
 * @param <T> an effectively immutable value type with stable {@link Object#equals(Object)} behavior
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface IConfigValueEditorInfo<T> {
	/**
	 * Get the saved value, including a restart-required change that is not effective yet.
	 * <p>
	 * This equals {@link IConfigValue#get()} when no restart is required or no change is pending. Setting this back to
	 * the effective value cancels a pending change. Synchronized remote server schemas replicate effective values only, so
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
	 * @since 0.3.0
	 */
	T getDefaultValue();

	/**
	 * Get the stable storage name of this config value.
	 *
	 * @since 0.3.0
	 */
	String getName();

	/**
	 * Get the translation key used for this config value's name.
	 *
	 * @since 0.3.0
	 */
	String getLocalizationKey();

	/**
	 * Get the edit mode hint for this value.
	 *
	 * @since 0.3.0
	 */
	ConfigValueEditMode getEditMode();

	/**
	 * Get the lifecycle boundary when saved changes become effective.
	 *
	 * @since 0.3.0
	 */
	ConfigValueRestartRequirement getRestartRequirement();

	/**
	 * Get the categories where config editors should show this value.
	 * <p>
	 * If this is empty, config editors can show the value in the category that contains it. Values may be shown in multiple
	 * editor categories. Categories are returned in editor category order from {@link IConfigSchema#getEditorCategories()}.
	 *
	 * @since 0.3.0
	 */
	@Unmodifiable
	List<? extends IConfigEditorCategory> getEditorCategories();

	/**
	 * Run code when this setting's {@link #getPendingValue()} changes, even if it is waiting for a restart.
	 *
	 * @param listener callback accepting the pending change
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingListener(IConfigValueChangeListener<T> listener);

	/**
	 * Observe the complete saved-value batch whenever it includes this setting.
	 *
	 * @param listener callback accepting the pending changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Get this setting's serializer for validation and editor integration.
	 *
	 * @since 0.3.0
	 */
	IConfigValueSerializer<T> getSerializer();
}
