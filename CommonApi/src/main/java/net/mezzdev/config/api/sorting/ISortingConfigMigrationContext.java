package net.mezzdev.config.api.sorting;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/**
 * Supplies a converted legacy order to a sorting config.
 * <p>
 * MezzConfig passes this to {@link ISortingConfigMigrator#migrate}. Call {@link #setSortedValues(Collection, List)} with
 * every value known to the old order and the values that were visible, in their saved order.
 *
 * @param <T> sortable value type
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface ISortingConfigMigrationContext<T> {
	/**
	 * Import the converted saved order.
	 * <p>
	 * The collections have the same meaning and validation rules as
	 * {@link ISortingConfig#setSortedValues(Collection, List)} and are copied before this method returns. If this is called
	 * more than once, the last order is used.
	 *
	 * @param allValues every migrated value known to this sort order
	 * @param sortedValues visible migrated values in their saved order
	 * @return this migration context
	 * @throws IllegalArgumentException if either collection violates the sorting config's contract
	 * @throws IllegalStateException if the migration callback has returned
	 *
	 * @since 0.3.0
	 */
	ISortingConfigMigrationContext<T> setSortedValues(Collection<T> allValues, List<T> sortedValues);
}
