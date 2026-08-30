package net.mezzdev.config.api.migration;

import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IConfigValue;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/**
 * Collects typed updates for one legacy config migration transaction.
 * <p>
 * Use only the context passed to {@link IConfigMigrator#migrate}. Values are snapshotted and validated when queued.
 * MezzConfig validates the complete transaction after the migrator returns, persists it synchronously in MezzConfig's
 * current formats, and applies it only if every update can be committed.
 *
 * @since 0.3.0
 */
@ApiStatus.NonExtendable
public interface IConfigMigrationContext {
	/**
	 * Set a value in the schema being migrated.
	 * If the same value is set more than once, the last value is used.
	 *
	 * @param configValue config value to update
	 * @param value migrated value
	 * @param <T> config value type
	 * @return this migration context
	 * @throws IllegalArgumentException if the value is invalid, cannot be safely serialized, was not created by
	 * MezzConfig, or does not belong to the schema being migrated
	 * @throws IllegalStateException if the migration callback has returned
	 *
	 * @since 0.3.0
	 */
	<T> IConfigMigrationContext set(IConfigValue<T> configValue, T value);

	/**
	 * Set a sorting config's known values and visible order in this migration transaction.
	 * <p>
	 * The collections have the same meaning and validation rules as
	 * {@link ISortingConfig#setSortedValues(Collection, List)}. They are copied before this method returns. The sorting
	 * config may be owned by the same registration or another MezzConfig registration, but it must have a local backing
	 * file.
	 *
	 * @param sortingConfig sorting config to update
	 * @param allValues every migrated value known to this sort order
	 * @param sortedValues visible migrated values in their saved order
	 * @param <T> sortable value type
	 * @return this migration context
	 * @throws IllegalArgumentException if either collection violates the sorting config's contract or the sorting config
	 * was not created by MezzConfig
	 * @throws IllegalStateException if the sorting config has no local backing file, cannot be safely updated, or the
	 * migration callback has returned
	 *
	 * @since 0.3.0
	 */
	<T> IConfigMigrationContext setSortedValues(
		ISortingConfig<T> sortingConfig,
		Collection<T> allValues,
		List<T> sortedValues
	);
}
