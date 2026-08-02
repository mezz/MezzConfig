package net.mezzdev.config.api.sorting;

import net.mezzdev.config.api.plugin.IConfigRegistration;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Stores and applies a user-configurable sort order for values discovered at runtime.
 * <p>
 * Create and register a string sort order here:
 * {@link IConfigRegistration#createSortingConfig(String, Comparator, boolean)}.
 * You can also pass your own implementation to APIs that explicitly accept {@link ISortingConfig}.
 *
 * @since 0.1.0
 */
public interface ISortingConfig<T> {
	/**
	 * Get the sorted visible values from the given complete value set.
	 *
	 * @param allValues every value that may be sorted
	 * @return the sorted visible values
	 *
	 * @since 0.1.0
	 */
	List<T> getSortedValues(Collection<T> allValues);

	/**
	 * Get the default sorted visible values from the given complete value set.
	 *
	 * @param allValues every value that may be sorted
	 * @return the default sorted visible values
	 *
	 * @since 0.1.0
	 */
	List<T> getDefaultSortedValues(Collection<T> allValues);

	/**
	 * Save a new sorted value list.
	 *
	 * @param sortedValues the sorted values to save
	 * @return true if the value was saved
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
	 * Removed values are not returned by {@link #getSortedValues(Collection)} until they are added back.
	 *
	 * @since 0.1.0
	 */
	boolean allowsRemovingValues();

	/**
	 * Register a callback invoked when this sort order changes.
	 *
	 * @param listener callback to run after the sort order changes
	 * @return a callback that removes this listener
	 *
	 * @since 0.1.0
	 */
	Runnable addChangeListener(Runnable listener);
}
