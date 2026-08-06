package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.files.IConfigManager;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents one registered config schema and its backing file.
 * <p>
 * Config schemas contain one or more {@link IConfigCategory},
 * and each category has one or more {@link IConfigValue}.
 * <p>
 * Create and register your schema here: {@link IConfigSchemaBuilder#build()}.
 * Get registered schemas here: {@link IConfigManager#getSchemas()}.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchema {
	/**
	 * Get the mod id that owns this config schema.
	 * <p>
	 * Config editors can use this to group schemas and attach generated config screens to the owning mod.
	 *
	 * @since 0.1.0
	 */
	String getModId();

	/**
	 * Get the current path of this config schema.
	 * <p>
	 * Normal client schemas always have a path. Context-specific schemas, such as client-world schemas, return an empty
	 * optional when there is no active backing file for the current game state.
	 * <p>
	 * Note that config values will read from this file automatically,
	 * and updating config values will save the file automatically,
	 * so you should not read or write this file yourself.
	 *
	 * @since 0.1.0
	 */
	Optional<Path> getPath();

	/**
	 * Get all the categories in this schema.
	 * Each category contains values that can be read or edited. Categories are returned in the order they were added to
	 * the schema builder.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigCategory> getCategories();

	/**
	 * Get all categories where config editors can show values.
	 * <p>
	 * Storage categories and editor-only categories are returned in the order they were added to the schema builder.
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IConfigEditorCategory> getEditorCategories();

	/**
	 * Apply several config value updates together.
	 * <p>
	 * Queue updates inside the callback. Queued values are validated before any values are changed. If validation
	 * succeeds, every changed value is updated before listeners are notified. If the callback throws, no queued updates
	 * are applied.
	 *
	 * @param updateBatch callback that queues updates
	 * @return changes that were applied
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IAppliedConfigValueChange<?>> batchUpdate(Consumer<IConfigBatchUpdater> updateBatch);

	/**
	 * Add a listener that is called with every batch of changes applied to this schema.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addListener(IConfigValueBatchChangeListener listener);

	/**
	 * Clear listeners from this schema and its values.
	 * Useful when tearing down per-runtime listeners.
	 *
	 * @since 0.1.0
	 */
	void clearListeners();
}
