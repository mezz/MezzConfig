package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueBatchChangeListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents one declared config schema.
 * A schema may be inactive or may hold synchronized server values without a local backing file; use {@link #isActive()}
 * and {@link #getPath()} to inspect its current runtime state.
 * <p>
 * Config schemas contain one or more {@link IConfigCategory},
 * and each category has one or more {@link IConfigValue}.
 * <p>
 * Create and register your schema here: {@link IConfigSchemaBuilder#build()}.
 * Get registered schemas here: {@link Configs#getSchemas()}.
 * <p>
 * Runtime methods are thread-safe and batches are atomic; concurrent operations are unordered. Listeners run
 * synchronously on the applying thread and are not dispatched to a game thread.
 * <p>
 * Listener notifications use immutable change lists and run after the complete applicable state is committed. Local
 * update batches schedule persistence before notifying; loads and synchronized snapshots notify after applying their
 * complete state. When one operation has both pending and effective changes, pending notifications run first.
 * Within either notification kind, each changed value's single-value listeners and then its value-scoped batch listeners
 * run in batch order; schema batch listeners run last. Listeners at the same scope run in registration order. Listener
 * failures are logged and do not prevent later listeners. Removal callbacks are idempotent and affect later notification
 * snapshots; listeners may register or remove listeners during a callback without changing the current snapshot.
 * Reentrant updates are allowed and synchronously dispatch a separate nested batch before the outer notification resumes;
 * callers must guard against reentrant update cycles.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchema {
	/**
	 * Get the stable identifier for this schema within its owning mod and schema type.
	 * <p>
	 * Automatically located schemas use their normalized relative config file name. Explicit-location schemas use their
	 * normalized absolute configured path. Treat this as an opaque storage identity rather than display text.
	 *
	 * @since 0.3.0
	 */
	String getId();

	/**
	 * Get the mod id that owns this config schema.
	 * <p>
	 * Config editors can use this to group schemas and attach generated config screens to the owning mod.
	 *
	 * @since 0.1.0
	 */
	String getModId();

	/**
	 * Get this schema's activation, authority, and location behavior.
	 *
	 * @since 0.3.0
	 */
	ConfigSchemaType getType();

	/**
	 * Return whether this schema currently has effective values for the current context.
	 * <p>
	 * {@link ConfigSchemaType#CLIENT} schemas are active on a physical client and inert on a dedicated server.
	 * {@link ConfigSchemaType#CLIENT_PER_WORLD} schemas are active on a physical client only while a singleplayer world or
	 * multiplayer connection is available, and are inert on a dedicated server. {@link ConfigSchemaType#SERVER} schemas
	 * are active while locally authoritative for a loaded world or after a client receives an authoritative server
	 * snapshot.
	 *
	 * @since 0.2.0
	 */
	boolean isActive();

	/**
	 * Get the current path of this config schema.
	 * <p>
	 * Client schemas have a path on a physical client. Client-per-world and locally authoritative server schemas have a
	 * path while their world or connection context is active. Inert client declarations on a dedicated server and schemas
	 * without an active context return an empty optional. An active synchronized remote server schema also returns an empty
	 * optional because its backing file belongs to the server; use {@link #isActive()} to distinguish it from an inactive
	 * schema.
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
	 * before any state changes. Saved values are persisted together. Values without a restart requirement become effective
	 * immediately; restart-required values remain pending. If the callback throws, no queued updates are applied.
	 *
	 * @param updateBatch callback that queues updates
	 * @return saved-value changes that were applied
	 *
	 * @throws IllegalArgumentException if a value is invalid, cannot be safely serialized, or does not belong to this schema
	 * @throws IllegalStateException if a non-empty batch is applied while this schema has no active local backing file,
	 * including synchronized server schemas viewed on a remote client
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<? extends IAppliedConfigValueChange<?>> batchUpdate(Consumer<IConfigBatchUpdater> updateBatch);

	/**
	 * Add a listener called exactly once for every non-empty batch of effective-value changes applied to this schema.
	 * This includes local updates, file loads and reloads, context changes, remote snapshots, disconnect resets, and restart
	 * promotions when they change effective values. Pending-only and unchanged batches do not invoke this listener.
	 * The listener receives the complete immutable effective batch after participating value listeners.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Add a listener called exactly once for every non-empty batch of pending saved-value changes applied to this schema.
	 * <p>
	 * Values without a restart requirement appear in both effective and pending notifications. Restart-required values
	 * appear in pending notifications when saved and effective notifications later when the applicable restart promotes
	 * them. Pending notifications run before effective notifications from the same operation. Unchanged batches do not
	 * invoke this listener. The listener receives the complete immutable pending batch after participating value listeners.
	 *
	 * @param listener callback accepting the pending changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener);
}
