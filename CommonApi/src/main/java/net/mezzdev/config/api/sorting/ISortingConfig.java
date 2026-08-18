package net.mezzdev.config.api.sorting;

import net.mezzdev.config.api.IConfigRegistration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Stores and applies a user-configurable sort order for values discovered at runtime.
 * <p>
 * Create and register a string sort order here:
 * {@link IConfigRegistration#createSortingConfig(String, Comparator, boolean)}.
 * <p>
 * Values must be non-null and effectively immutable while held by the sorting config. Their
 * {@link Object#equals(Object)} and {@link Object#hashCode()} results must remain stable, because equality identifies
 * the same sortable value across saved preferences and runtime value collections.
 * <p>
 * Methods are thread-safe, but concurrent operations have no defined order. Listeners run synchronously after an update
 * is committed.
 *
 * @param <T> effectively immutable value type with stable equality and hash codes
 *
 * @since 0.1.0
 */
@ApiStatus.NonExtendable
public interface ISortingConfig<T> {
	/**
	 * Get the sorted visible values from the given complete value set.
	 * The saved preference is reconciled against the values supplied to each call, so newly added and removed runtime
	 * values are reflected in the result. Newly discovered values are visible by default, including when
	 * {@link #allowsRemovingValues()} is {@code true}; only values that have been explicitly removed remain hidden.
	 *
	 * @param allValues every value that may be sorted
	 * @return an unmodifiable, duplicate-free snapshot of the sorted visible values
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<T> getSortedValues(Collection<T> allValues);

	/**
	 * Get the default sorted visible values from the given complete value set.
	 *
	 * @param allValues every value that may be sorted
	 * @return an unmodifiable, duplicate-free snapshot of the default sorted visible values
	 *
	 * @since 0.1.0
	 */
	@Unmodifiable
	List<T> getDefaultSortedValues(Collection<T> allValues);

	/**
	 * Set and persist a new sorted value list.
	 * The list is copied to an unmodifiable snapshot before this method returns.
	 * <p>
	 * When removal is allowed, a value from the most recent {@code allValues} collection is explicitly hidden when it is
	 * omitted from this list. Previously hidden values are made visible again when they are included.
	 *
	 * @param sortedValues the non-null, duplicate-free sorted values to save
	 * @return {@code true} if the sort order changed, or {@code false} if it was equal to the saved sort order
	 *
	 * @throws IllegalArgumentException if the list contains null or duplicate values
	 *
	 * @since 0.1.0
	 */
	boolean setSortedValues(List<T> sortedValues);

	/**
	 * Get a comparator that follows this sort order.
	 *
	 * @param allValues every value that may be sorted
	 * @return a comparator using this sort order
	 *
	 * @since 0.1.0
	 */
	Comparator<T> getComparator(Collection<T> allValues);

	/**
	 * Return whether the given value is present in this sort order.
	 *
	 * @param allValues every value that may be sorted
	 * @param value value to check
	 * @return true when the value is visible
	 *
	 * @since 0.1.0
	 */
	boolean isVisible(Collection<T> allValues, T value);

	/**
	 * Return whether values may be removed from this sort order.
	 * Explicitly removed values are not returned by {@link #getSortedValues(Collection)} until they are added back with
	 * {@link #setSortedValues(List)}. Values discovered after an order was saved remain visible by default.
	 *
	 * @since 0.1.0
	 */
	boolean allowsRemovingValues();

	/**
	 * Register a callback invoked when this sort order changes.
	 * <p>
	 * Callbacks run synchronously on the thread calling {@link #setSortedValues(List)}, after the new in-memory order is
	 * committed and persistence has been attempted. Registration and removal are thread-safe. A runtime exception is logged
	 * without preventing later callbacks.
	 *
	 * @param listener callback to run after the sort order changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addChangeListener(Runnable listener);
}
