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
import java.util.Set;

public final class SortingConfig implements ISortingConfig<String> {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String VISIBLE_SECTION = "[visible]";
	private static final String HIDDEN_SECTION = "[hidden]";

	private final @Nullable Path defaultPath;
	private final Path path;
	private final Comparator<String> defaultSortOrder;
	private final boolean allowsRemovingValues;
	private final List<Runnable> changeListeners = new ArrayList<>();
	private List<String> lastAllValues = List.of();
	@Nullable
	private SavedValues savedValues;
	private boolean savedValuesNeedWrite;

	public SortingConfig(
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this(null, path, defaultSortOrder, allowsRemovingValues);
	}

	public SortingConfig(
		@Nullable Path defaultPath,
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this.defaultPath = defaultPath;
		this.path = Objects.requireNonNull(path, "path");
		this.defaultSortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
		this.allowsRemovingValues = allowsRemovingValues;
	}

	@Override
	public List<String> getSortedValues(Collection<String> allValues) {
		List<String> allValuesSnapshot = getDistinctValues(allValues, "allValues");
		writeDefaultIfMissing(allValuesSnapshot);
		SavedValues previousSavedValues = getSavedValues();
		SavedValues reconciledSavedValues = addDiscoveredValues(previousSavedValues, allValuesSnapshot);
		this.lastAllValues = allValuesSnapshot;
		if (savedValuesNeedWrite || !previousSavedValues.equals(reconciledSavedValues)) {
			this.savedValues = reconciledSavedValues;
			this.savedValuesNeedWrite = !save(reconciledSavedValues);
		}
		return getCurrentVisibleValues(reconciledSavedValues, allValuesSnapshot);
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
		SavedValues previousSavedValues = getSavedValues();
		SavedValues updatedSavedValues = updateSavedValues(previousSavedValues, sortedValuesCopy);
		boolean changed = !previousSavedValues.equals(updatedSavedValues);
		if (savedValuesNeedWrite || changed) {
			this.savedValues = updatedSavedValues;
			this.savedValuesNeedWrite = !save(updatedSavedValues);
		}
		if (!changed) {
			return false;
		}
		notifyListeners();
		return true;
	}

	private SavedValues addDiscoveredValues(SavedValues savedValues, List<String> allValues) {
		Set<String> knownValues = getKnownValues(savedValues);
		List<String> discoveredValues = allValues.stream()
			.filter(knownValues::add)
			.sorted(defaultSortOrder)
			.toList();
		if (discoveredValues.isEmpty()) {
			return savedValues;
		}
		List<String> visibleValues = new ArrayList<>(savedValues.visibleValues());
		visibleValues.addAll(discoveredValues);
		return new SavedValues(visibleValues, savedValues.hiddenValues());
	}

	private SavedValues updateSavedValues(SavedValues savedValues, List<String> sortedValues) {
		Set<String> sortedValuesSet = new HashSet<>(sortedValues);
		Set<String> currentValues = new HashSet<>(lastAllValues);
		List<String> visibleValues = new ArrayList<>(sortedValues);
		for (String previouslyVisible : savedValues.visibleValues()) {
			if (!sortedValuesSet.contains(previouslyVisible) && !currentValues.contains(previouslyVisible)) {
				visibleValues.add(previouslyVisible);
			}
		}

		if (!allowsRemovingValues) {
			Set<String> visibleValuesSet = new HashSet<>(visibleValues);
			List<String> requiredVisibleValues = new ArrayList<>();
			for (String value : lastAllValues) {
				if (visibleValuesSet.add(value)) {
					requiredVisibleValues.add(value);
				}
			}
			for (String value : savedValues.hiddenValues()) {
				if (visibleValuesSet.add(value)) {
					requiredVisibleValues.add(value);
				}
			}
			requiredVisibleValues.sort(defaultSortOrder);
			visibleValues.addAll(requiredVisibleValues);
			return new SavedValues(visibleValues, List.of());
		}

		Set<String> hiddenValues = new LinkedHashSet<>(savedValues.hiddenValues());
		hiddenValues.removeAll(sortedValuesSet);
		lastAllValues.stream()
			.filter(value -> !sortedValuesSet.contains(value))
			.sorted(defaultSortOrder)
			.forEach(hiddenValues::add);
		return new SavedValues(visibleValues, List.copyOf(hiddenValues));
	}

	private static List<String> getCurrentVisibleValues(SavedValues savedValues, List<String> allValues) {
		Set<String> currentValues = new HashSet<>(allValues);
		return savedValues.visibleValues().stream()
			.filter(currentValues::contains)
			.toList();
	}

	private static Set<String> getKnownValues(SavedValues savedValues) {
		Set<String> knownValues = new HashSet<>(savedValues.visibleValues());
		knownValues.addAll(savedValues.hiddenValues());
		return knownValues;
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

	private boolean save(SavedValues savedValues) {
		try {
			write(path, savedValues);
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to save sort order config to file {}", this.path, e);
			return false;
		}
	}

	private static void write(Path path, SavedValues savedValues) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		List<String> serialized = new ArrayList<>();
		serialized.add(VISIBLE_SECTION);
		savedValues.visibleValues().stream()
			.map(SortingConfig::encodeValue)
			.forEach(serialized::add);
		serialized.add(HIDDEN_SECTION);
		savedValues.hiddenValues().stream()
			.map(SortingConfig::encodeValue)
			.forEach(serialized::add);
		Files.write(path, serialized, StandardCharsets.UTF_8);
	}

	private void writeDefaultIfMissing(List<String> allValues) {
		Path defaultPath = this.defaultPath;
		if (defaultPath == null || Files.exists(defaultPath)) {
			return;
		}
		SavedValues defaultValues = new SavedValues(
			allValues.stream()
				.sorted(defaultSortOrder)
				.toList(),
			List.of()
		);
		try {
			write(defaultPath, defaultValues);
		} catch (IOException e) {
			LOGGER.error("Failed to save default sort order config to file {}", defaultPath, e);
		}
	}

	private SavedValues getSavedValues() {
		SavedValues savedValues = this.savedValues;
		if (savedValues == null) {
			SavedValues loadedSavedValues = loadSavedValuesFromFile();
			savedValues = normalizeSavedValues(loadedSavedValues);
			this.savedValues = savedValues;
			this.savedValuesNeedWrite = !loadedSavedValues.equals(savedValues);
		}
		return savedValues;
	}

	private SavedValues loadSavedValuesFromFile() {
		Path loadPath = path;
		if (!Files.exists(loadPath)) {
			loadPath = defaultPath;
		}
		if (loadPath == null || !Files.exists(loadPath)) {
			return SavedValues.EMPTY;
		}
		try {
			List<String> lines = Files.readAllLines(loadPath, StandardCharsets.UTF_8);
			return parseSavedValues(lines);
		} catch (IOException e) {
			LOGGER.error("Failed to load sort order config from file: {}", loadPath, e);
			return SavedValues.EMPTY;
		}
	}

	private static SavedValues parseSavedValues(List<String> lines) {
		List<String> visibleValues = new ArrayList<>();
		List<String> hiddenValues = new ArrayList<>();
		List<String> currentSection = null;
		for (String line : lines) {
			if (VISIBLE_SECTION.equals(line)) {
				currentSection = visibleValues;
			} else if (HIDDEN_SECTION.equals(line)) {
				currentSection = hiddenValues;
			} else if (currentSection != null && !line.isBlank()) {
				currentSection.add(decodeValue(line));
			}
		}
		return new SavedValues(visibleValues, hiddenValues);
	}

	private SavedValues normalizeSavedValues(SavedValues savedValues) {
		Set<String> visibleValues = new LinkedHashSet<>(savedValues.visibleValues());
		Set<String> hiddenValues = new LinkedHashSet<>(savedValues.hiddenValues());
		hiddenValues.removeAll(visibleValues);
		if (!allowsRemovingValues && !hiddenValues.isEmpty()) {
			List<String> previouslyHiddenValues = new ArrayList<>(hiddenValues);
			previouslyHiddenValues.sort(defaultSortOrder);
			visibleValues.addAll(previouslyHiddenValues);
			hiddenValues.clear();
		}
		return new SavedValues(List.copyOf(visibleValues), List.copyOf(hiddenValues));
	}

	private static String encodeValue(String value) {
		if (value.isEmpty() || value.startsWith("\\") ||
			VISIBLE_SECTION.equals(value) || HIDDEN_SECTION.equals(value)
		) {
			return "\\" + value;
		}
		return value;
	}

	private static String decodeValue(String value) {
		if (value.startsWith("\\")) {
			return value.substring(1);
		}
		return value;
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

	private record SavedValues(
		List<String> visibleValues,
		List<String> hiddenValues
	) {
		private static final SavedValues EMPTY = new SavedValues(List.of(), List.of());

		private SavedValues {
			visibleValues = List.copyOf(visibleValues);
			hiddenValues = List.copyOf(hiddenValues);
		}
	}

}
