package net.mezzdev.config.api.schema;

import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.schema.builder.IConfigSchemaBuilder;
import net.mezzdev.config.api.schema.category.IConfigCategory;
import net.mezzdev.config.api.schema.category.IConfigEditorCategory;
import net.mezzdev.config.api.schema.update.IConfigBatchUpdater;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.change.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.change.IConfigValueBatchChangeListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Provides runtime access to a group of config values stored and activated together.
 * <p>
 * Get an instance from {@link IConfigSchemaBuilder#build()}. Most mods keep the {@link IConfigValue} objects they build and
 * use the schema when several values must change atomically. Config screens and other integrations can discover schemas
 * through {@link Configs#getSchemas()} and inspect their categories.
 * <p>
 * Runtime methods are thread-safe. Listeners run synchronously after a complete change has been applied; listener failures
 * are logged, and reentrant updates start a separate nested notification. Pending listeners run before effective listeners
 * for the same operation. Within either kind, single-value listeners run before value-scoped batch listeners, and schema
 * batch listeners run last. All listeners for an operation are snapshotted before its first callback. Listener changes
 * made during a callback affect subsequent operations, including reentrant updates, but not the operation in progress.
 * <p>
 * Listener registrations normally live as long as this schema, which is usually the full mod lifetime, so callers may
 * ignore their returned removal callbacks. Keep and run a removal callback when its listener captures a shorter-lived
 * object, such as a screen, reloadable runtime, or connection-specific component. Client-per-world schemas retain their
 * listeners across world changes and notify them when effective values change.
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface IConfigSchema {
	/**
	 * Get the stable storage identifier for integrations that need to distinguish this schema.
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
	 * Return whether this schema currently supplies values for the running game context.
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
	 * Get the current local backing file path, if one exists.
	 * <p>
	 * Client schemas have a path on a physical client. Client-per-world and locally authoritative server schemas have a
	 * path while their world or connection context is active. Inert client declarations on a dedicated server and schemas
	 * without an active context return an empty optional. An active synchronized remote server schema also returns an empty
	 * optional because its backing file belongs to the server; use {@link #isActive()} to distinguish it from an inactive
	 * schema.
	 * <p>
	 * MezzConfig owns this file; mods should use {@link IConfigValue} rather than reading or writing it directly.
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
	 * Change several values as one atomic user action.
	 * <p>
	 * Use this for related settings that must not expose a partially updated state. Call
	 * {@link IConfigBatchUpdater#set(IConfigValue, Object)} inside the callback. If any update is invalid or the callback
	 * throws, none of them are applied.
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
	 * Listen for any effective values in this schema changing together.
	 * <p>
	 * Use this when derived state depends on multiple settings. Pending restart-required edits are reported later, when they
	 * become effective.
	 *
	 * @param listener callback accepting the applied changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addBatchListener(IConfigValueBatchChangeListener listener);

	/**
	 * Listen for any saved values in this schema changing together, including changes waiting for a restart.
	 * <p>
	 * Use this for editors or diagnostics that need to display what is saved rather than only what is currently effective.
	 *
	 * @param listener callback accepting the pending changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.3.0
	 */
	Runnable addPendingBatchListener(IConfigValueBatchChangeListener listener);
}
