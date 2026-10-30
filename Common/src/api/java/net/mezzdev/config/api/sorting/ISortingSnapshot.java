package net.mezzdev.config.api.sorting;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;

/**
 * An immutable view of the visible values and ordering resolved from a sorting config.
 * <p>
 * Config edits and changes to the available values do not update this snapshot. Obtain a new snapshot from
 * {@link ISortingConfig#getSnapshot} after either changes. Snapshots may be shared between threads, provided the values
 * and the config's default comparator are safe to share.
 *
 * @param <T> effectively immutable value type with stable equality and hash codes
 * @since 0.6.10
 */
@ApiStatus.NonExtendable
public interface ISortingSnapshot<T> {
	/**
	 * Return the duplicate-free visible values in their resolved order.
	 *
	 * @return an unmodifiable list of visible values
	 * @since 0.6.10
	 */
	@Unmodifiable
	List<T> getSortedValues();

	/**
	 * Return whether a value is visible in this snapshot, using value equality.
	 *
	 * @param value value to check
	 * @return false for hidden values and values absent when the snapshot was created
	 * @since 0.6.10
	 */
	boolean isVisible(T value);

	/**
	 * Return a comparator using this snapshot's order.
	 * Visible values sort first in their resolved order. All other values, including hidden values, sort afterward
	 * using the config's default comparator.
	 *
	 * @return a comparator independent of subsequent config edits
	 * @since 0.6.10
	 */
	Comparator<T> getComparator();
}
