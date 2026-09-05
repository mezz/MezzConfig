package net.mezzdev.config.api.sorting;

import net.mezzdev.config.api.IConfigRegistration;
import net.mezzdev.config.api.migration.IConfigMigrationResult;
import net.mezzdev.config.api.migration.ISortingConfigMigrator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Stores a user's preferred order for values discovered at runtime.
 * <p>
 * Pass the currently available values to {@link #getSortedValues(Collection)} whenever they need to be displayed. New
 * values are inserted using the default comparator, missing values are ignored, and any user-hidden values remain hidden.
 * Save edits with {@link #setSortedValues(Collection, List)}.
 * <p>
 * Create one through {@link IConfigRegistration#createSortingConfig(String, Comparator, boolean)}. Use the serializer
 * overload for mod-specific value types; those values must be immutable and have stable equality and serialization.
 * <p>
 * To import an order from an older file format, register a migrator with {@link #setLegacyMigration} before the saved order
 * is first used. Runtime methods and listener registration are thread-safe.
 * Listener registrations normally live as long as this sorting config, which is usually the full mod lifetime, so callers
 * may ignore their returned removal callbacks. Keep and run a removal callback when its listener captures a shorter-lived
 * object, such as a screen, reloadable runtime, or connection-specific component.
 *
 * @param <T> effectively immutable value type with stable equality and hash codes
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface ISortingConfig<T> {
	/**
	 * Apply the saved user preference to the values currently available.
	 *
	 * @param allValues every value that may be sorted
	 * @return an unmodifiable, duplicate-free snapshot of the sorted visible values
	 * @throws IllegalArgumentException if the supplied values violate the serializer contract or the reconciled file-backed
	 * order cannot be safely serialized
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<T> getSortedValues(Collection<T> allValues);

	/**
	 * Sort the currently available values without applying the user's saved preference.
	 *
	 * @param allValues every value that may be sorted
	 * @return an unmodifiable, duplicate-free snapshot of the default sorted visible values
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<T> getDefaultSortedValues(Collection<T> allValues);

	/**
	 * Save the user's preferred order for the values currently available.
	 * <p>
	 * When removal is allowed, omitting an available value hides it. Otherwise omitted values remain visible and are
	 * appended in default order.
	 *
	 * @param allValues every value that may be sorted
	 * @param sortedValues the non-null, duplicate-free sorted values to save
	 * @return {@code true} if the sort order changed, or {@code false} if it was equal to the saved sort order
	 *
	 * @throws IllegalArgumentException if a collection contains null, {@code sortedValues} contains duplicates, a value in
	 * {@code sortedValues} is not present in {@code allValues}, the serializer contract is violated, or the file-backed order
	 * cannot be safely serialized
	 *
	 * @since 0.1.0
	 */
	boolean setSortedValues(Collection<T> allValues, List<T> sortedValues);

	/**
	 * Get a comparator that applies the saved preference to the currently available values.
	 *
	 * @param allValues every value that may be sorted
	 * @return a comparator using this sort order
	 *
	 * @since 0.1.0
	 */
	Comparator<T> getComparator(Collection<T> allValues);

	/**
	 * Return whether the user-visible order currently includes a value.
	 *
	 * @param allValues every value that may be sorted
	 * @param value value to check
	 * @return true when the value is visible
	 *
	 * @since 0.1.0
	 */
	boolean isVisible(Collection<T> allValues, T value);

	/**
	 * Return whether users may hide values by removing them from their saved order.
	 *
	 * @since 0.1.0
	 */
	boolean allowsRemovingValues();

	/**
	 * Run code after the user's saved order changes.
	 * <p>
	 * Use this to refresh a view that displays the sorted values. Callbacks run synchronously after the new order is active.
	 *
	 * @param listener callback to run after the sort order changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addChangeListener(Runnable listener);

	/**
	 * Import a saved order from an older file when this sorting config does not have a destination file yet.
	 * <p>
	 * Register this immediately after creating the sorting config, before calling methods that load or save its order. The
	 * paths are checked in order when the saved order is first needed. MezzConfig preserves and backs up the selected source,
	 * validates the migrated order, and saves it atomically in the current sorting format.
	 *
	 * @param legacyPaths ordered candidate legacy file paths; must not be empty
	 * @param migrator callback that parses the selected file and supplies the migrated order
	 * @return this sorting config
	 * @throws IllegalArgumentException if the path list is empty, contains null or duplicate normalized paths, or a path
	 * cannot be converted to an absolute path
	 * @throws IllegalStateException if migration was already registered or the saved order was already loaded or changed
	 *
	 * @see ISortingConfigMigrator#onMigrationComplete(IConfigMigrationResult)
	 *
	 * @since 0.3.0
	 */
	ISortingConfig<T> setLegacyMigration(List<Path> legacyPaths, ISortingConfigMigrator<T> migrator);
}
