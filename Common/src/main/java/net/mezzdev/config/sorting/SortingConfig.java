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
import java.util.LinkedHashSet;
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
	private Optional<List<String>> savedValues;

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
		List<String> allValuesSnapshot = getDistinctValues(allValues, "allValues");
		Optional<List<String>> previousSavedValues = getSavedValues();
		List<String> sortedValues = reconcile(allValuesSnapshot, previousSavedValues);
		boolean changed = previousSavedValues
			.map(saved -> !saved.equals(sortedValues))
			.orElse(true);
		if (changed) {
			this.savedValues = Optional.of(sortedValues);
			save(sortedValues);
		}
		return sortedValues;
	}

	@Override
	public List<String> getDefaultSortedValues(Collection<String> allValues) {
		return getDistinctValues(allValues, "allValues").stream()
			.sorted(defaultSortOrder)
			.toList();
	}

	@Override
	public boolean setSortedValues(List<String> sortedValues) {
		Objects.requireNonNull(sortedValues, "sortedValues");
		List<String> sortedValuesCopy = copySortedValues(sortedValues);
		Optional<List<String>> previousSavedValues = getSavedValues();
		if (previousSavedValues.filter(sortedValuesCopy::equals).isPresent()) {
			return false;
		}
		this.savedValues = Optional.of(sortedValuesCopy);
		save(sortedValuesCopy);
		notifyListeners();
		return true;
	}

	private List<String> reconcile(List<String> allValues, Optional<List<String>> previousSavedValues) {
		final Comparator<String> sortOrder = previousSavedValues
			.map(s -> {
				Comparator<String> existingOrder = Comparator.comparingInt(t -> indexOfSort(s.indexOf(t)));
				return existingOrder.thenComparing(defaultSortOrder);
			})
			.orElse(defaultSortOrder);

		return getValuesToSort(allValues, previousSavedValues).stream()
			.distinct()
			.sorted(sortOrder)
			.toList();
	}

	private static List<String> copySortedValues(List<String> sortedValues) {
		final List<String> copy;
		try {
			copy = List.copyOf(sortedValues);
		} catch (NullPointerException e) {
			throw new IllegalArgumentException("sortedValues must not contain null values.", e);
		}
		if (new HashSet<>(copy).size() != copy.size()) {
			throw new IllegalArgumentException("sortedValues must not contain duplicate values.");
		}
		return copy;
	}

	private static List<String> getDistinctValues(Collection<String> values, String parameterName) {
		Objects.requireNonNull(values, parameterName);
		Set<String> distinctValues = new LinkedHashSet<>();
		for (String value : values) {
			if (value == null) {
				throw new IllegalArgumentException(parameterName + " must not contain null values.");
			}
			distinctValues.add(value);
		}
		return List.copyOf(distinctValues);
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

	private Optional<List<String>> getSavedValues() {
		Optional<List<String>> savedValues = this.savedValues;
		if (savedValues == null) {
			savedValues = loadSavedValuesFromFile();
			this.savedValues = savedValues;
		}
		return savedValues;
	}

	private Optional<List<String>> loadSavedValuesFromFile() {
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
		List<String> sortedValues = getSortedValues(allValues);
		Comparator<String> savedOrder = Comparator.comparingInt(value -> indexOfSort(sortedValues.indexOf(value)));
		return savedOrder.thenComparing(defaultSortOrder);
	}

	@Override
	public boolean isVisible(Collection<String> allValues, String value) {
		Objects.requireNonNull(value, "value");
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
			try {
				listener.run();
			} catch (RuntimeException e) {
				LOGGER.error("Sort order config listener failed for {}.", path, e);
			}
		}
	}
}
