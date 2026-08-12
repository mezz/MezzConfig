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
import java.util.concurrent.CompletableFuture;
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
	 * Get the ownership and activation model for this schema.
	 *
	 * @since 0.2.0
	 */
	ConfigSchemaType getType();

	/**
	 * Return whether this schema currently has effective values for the current context.
	 * <p>
	 * Always-active client schemas return {@code true}. Client-world schemas return {@code true} while connected to a
	 * world or server. Server schemas return {@code true} on the active server and after a client receives the server's
	 * authoritative snapshot.
	 *
	 * @since 0.2.0
	 */
	boolean isActive();

	/**
	 * Return whether the local user can currently request edits to this schema.
	 * <p>
	 * Active client-owned schemas are editable. A synchronized server schema is editable only when the server reported
	 * that the local player has its operator permission level. The server checks permission again for every request, so
	 * this is a presentation hint rather than an authorization boundary.
	 *
	 * @since 0.2.0
	 */
	boolean canEdit();

	/**
	 * Get the current path of this config schema.
	 * <p>
	 * Normal client schemas always have a player-specific path, although the file is not created until the player changes
	 * a value. Context-specific schemas, such as client-world schemas, return an empty optional when there is no active
	 * backing file for the current game state. A synchronized remote server schema also returns an empty optional because
	 * its backing file belongs to the server; use {@link #isActive()} to distinguish that from an inactive schema.
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
	 * Queue updates inside the callback. Queued values are snapshotted immediately and the complete batch is validated
	 * before any values are changed. If validation succeeds, every changed value is updated and persistence is scheduled
	 * before listeners are notified. If the callback throws, no queued updates are applied.
	 *
	 * @param updateBatch callback that queues updates
	 * @return changes that were applied
	 *
	 * @throws IllegalArgumentException if a value is invalid or does not belong to this schema
	 * @throws IllegalStateException if this context-specific schema is currently inactive
	 * @throws IllegalStateException if this is a server schema; use {@link #requestBatchUpdate(Consumer)} instead
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IAppliedConfigValueChange<?>> batchUpdate(Consumer<IConfigBatchUpdater> updateBatch);

	/**
	 * Request several config value updates together.
	 * <p>
	 * For client-owned schemas, this has the same validation, persistence, and listener behavior as
	 * {@link #batchUpdate(Consumer)} and returns an already-completed future. For a server schema, the values are sent to
	 * the server without changing the local snapshot. The future completes after the server applies the accepted request
	 * and sends its authoritative result. It completes exceptionally if the request cannot be sent, permission is denied,
	 * the server rejects a value, or the server does not respond before the implementation's bounded request timeout.
	 * <p>
	 * Config editors should prefer this method so the same editing flow works for both client- and server-owned schemas.
	 * Queued values are snapshotted and locally validated before the request is sent.
	 *
	 * @param updateBatch callback that queues updates
	 * @return completion of the local update or server request
	 *
	 * @throws IllegalArgumentException if a value is invalid or does not belong to this schema
	 * @throws IllegalStateException if this schema is currently inactive
	 *
	 * @since 0.2.0
	 */
	CompletableFuture<Void> requestBatchUpdate(Consumer<IConfigBatchUpdater> updateBatch);

	/**
	 * Add a listener that is called with every batch of changes applied to this schema.
	 * See {@link IConfigValueBatchChangeListener} for callback execution and failure behavior.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addListener(IConfigValueBatchChangeListener listener);
}
