package net.mezzdev.config.sorting;

import net.mezzdev.config.api.migration.ISortingConfigMigrationContext;

import java.util.Collection;
import java.util.List;

final class SortingConfigMigrationContext<T> implements ISortingConfigMigrationContext<T> {
	private final SortingConfig<T> sortingConfig;
	private SortingConfig.MigrationUpdate<T> update;
	private boolean closed;

	SortingConfigMigrationContext(SortingConfig<T> sortingConfig) {
		this.sortingConfig = sortingConfig;
	}

	@Override
	public SortingConfigMigrationContext<T> setSortedValues(
		Collection<T> allValues,
		List<T> sortedValues
	) {
		checkOpen();
		update = sortingConfig.prepareMigrationUpdate(allValues, sortedValues);
		return this;
	}

	SortingConfig.MigrationUpdate<T> getUpdate() {
		if (update == null) {
			throw new IllegalStateException("Sorting migration did not supply a saved order.");
		}
		return update;
	}

	void close() {
		closed = true;
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException("Sorting config migration context has already closed.");
		}
	}
}
