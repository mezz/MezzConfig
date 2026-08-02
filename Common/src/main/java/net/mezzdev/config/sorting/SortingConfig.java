package net.mezzdev.config.sorting;

import net.mezzdev.config.api.sorting.ISortingConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SortingConfig implements ISortingConfig<String> {
	private static final Logger LOGGER = LogManager.getLogger();
	private final Path path;
	private final Comparator<String> defaultSortOrder;
	private final boolean allowsRemovingValues;
	private final List<Runnable> changeListeners = new ArrayList<>();
	@Nullable
	private List<String> sorted;

	public SortingConfig(
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this.path = Objects.requireNonNull(path, "path");
		this.defaultSortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
		this.allowsRemovingValues = allowsRemovingValues;
	}

	@Override
	public List<String> getSortedValues(Collection<String> allValues) {
		Objects.requireNonNull(allValues, "allValues");
		List<String> sorted = this.sorted;
		if (sorted == null) {
			sorted = load(allValues);
			this.sorted = sorted;
		}
		return sorted;
	}

	@Override
	public List<String> getDefaultSortedValues(Collection<String> allValues) {
		Objects.requireNonNull(allValues, "allValues");
		return allValues.stream()
			.distinct()
			.sorted(defaultSortOrder)
			.toList();
	}

	@Override
	public boolean setSortedValues(List<String> sortedValues) {
		Objects.requireNonNull(sortedValues, "sortedValues");
		List<String> sortedValuesCopy = List.copyOf(sortedValues);
		try {
			write(sortedValuesCopy);
			this.sorted = sortedValuesCopy;
			notifyListeners();
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to save sort order config to file {}", path, e);
			return false;
		}
	}

	private List<String> load(Collection<String> allValues) {
		final Optional<List<String>> previousSorted = loadSortedFromFile();
		final Comparator<String> sortOrder = previousSorted
			.map(s -> {
				Comparator<String> existingOrder = Comparator.comparingInt(t -> indexOfSort(s.indexOf(t)));
				return existingOrder.thenComparing(defaultSortOrder);
			})
			.orElse(defaultSortOrder);

		List<String> sortedValues = getValuesToSort(allValues, previousSorted).stream()
			.distinct()
			.sorted(sortOrder)
			.toList();

		boolean changed = previousSorted
			.map(s -> !Objects.equals(s, sortedValues))
			.orElse(true);
		if (changed) {
			save(sortedValues);
		}
		return sortedValues;
	}

	private void save(List<String> sortedValues) {
		try {
			write(sortedValues);
		} catch (IOException e) {
			LOGGER.error("Failed to save sort order config to file {}", this.path, e);
		}
	}

	private void write(List<String> sortedValues) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(path, sortedValues, StandardCharsets.UTF_8);
	}

	private Optional<List<String>> loadSortedFromFile() {
		if (Files.exists(path)) {
			try {
				List<String> result = Files.readAllLines(path, StandardCharsets.UTF_8)
					.stream()
					.filter(value -> !value.isBlank())
					.toList();
				return Optional.of(result);
			} catch (IOException e) {
				LOGGER.error("Failed to load sort order config from file: {}", path, e);
			}
		}
		return Optional.empty();
	}

	private Collection<String> getValuesToSort(Collection<String> allValues, Optional<List<String>> previousSorted) {
		if (!allowsRemovingValues) {
			return allValues;
		}
		return previousSorted
			.<Collection<String>>map(sortedValues -> {
				Set<String> validValues = new HashSet<>(allValues);
				return sortedValues.stream()
					.filter(validValues::contains)
					.toList();
			})
			.orElse(allValues);
	}

	private static int indexOfSort(int index) {
		if (index < 0) {
			return Integer.MAX_VALUE;
		}
		return index;
	}

	@Override
	public Comparator<String> getComparator(Collection<String> allValues) {
		Objects.requireNonNull(allValues, "allValues");
		Comparator<String> savedOrder = Comparator.comparingInt(value -> indexOfSort(getSortedValues(allValues).indexOf(value)));
		return savedOrder.thenComparing(defaultSortOrder);
	}

	@Override
	public boolean isVisible(Collection<String> allValues, String value) {
		Objects.requireNonNull(allValues, "allValues");
		return getSortedValues(allValues).contains(value);
	}

	@Override
	public boolean allowsRemovingValues() {
		return allowsRemovingValues;
	}

	@Override
	public Runnable addChangeListener(Runnable listener) {
		Objects.requireNonNull(listener, "listener");
		this.changeListeners.add(listener);
		return () -> this.changeListeners.remove(listener);
	}

	private void notifyListeners() {
		List<Runnable> listeners = List.copyOf(changeListeners);
		for (Runnable listener : listeners) {
			listener.run();
		}
	}
}
