package net.mezzdev.config.sorting;

import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.ini.IniFileReader;
import net.mezzdev.config.ini.IniValue;
import net.mezzdev.config.ini.IniValueCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
	private static final String ENCODED_VALUE_PREFIX = "\\=";
	private static final int MAX_BACKUPS = 5;
	private static final int MAX_LOGGED_PROBLEMS = 100;

	private final @Nullable Path defaultPath;
	private final Path path;
	private final Comparator<String> defaultSortOrder;
	private final boolean allowsRemovingValues;
	private final List<Runnable> changeListeners = new ArrayList<>();
	private List<String> lastAllValues = List.of();
	@Nullable
	private SavedValues savedValues;
	private boolean savedValuesNeedWrite;
	private boolean writesBlockedByReadFailure;
	private @Nullable Path correctionPath;

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
		if (writesBlockedByReadFailure) {
			return false;
		}
		Path savePath = correctionPath;
		if (savePath == null) {
			savePath = path;
		}
		try {
			if (correctionPath != null) {
				ConfigFileUtil.backUpFile(savePath, MAX_BACKUPS);
			}
			write(savePath, savedValues);
			correctionPath = null;
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to save sort order config to file {}", savePath, e);
			return false;
		}
	}

	private static void write(Path path, SavedValues savedValues) throws IOException {
		List<String> serialized = new ArrayList<>();
		serialized.add(VISIBLE_SECTION);
		savedValues.visibleValues().stream()
			.map(SortingConfig::encodeValue)
			.forEach(serialized::add);
		serialized.add(HIDDEN_SECTION);
		savedValues.hiddenValues().stream()
			.map(SortingConfig::encodeValue)
			.forEach(serialized::add);
		ConfigFileUtil.writeUsingTempFile(path, serialized);
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
			LoadedSavedValues loaded = loadSavedValuesFromFile();
			SavedValues loadedSavedValues = loaded.savedValues();
			savedValues = normalizeSavedValues(loadedSavedValues);
			boolean normalized = !loadedSavedValues.equals(savedValues);
			this.savedValues = savedValues;
			this.savedValuesNeedWrite = loaded.needsCorrection() || normalized;
			this.writesBlockedByReadFailure = loaded.readFailed();
			this.correctionPath = null;
			if (loaded.needsCorrection() || normalized) {
				this.correctionPath = loaded.loadPath();
			}
		}
		return savedValues;
	}

	private LoadedSavedValues loadSavedValuesFromFile() {
		Path loadPath = path;
		if (!Files.exists(loadPath)) {
			loadPath = defaultPath;
		}
		if (loadPath == null || !Files.exists(loadPath)) {
			return new LoadedSavedValues(SavedValues.EMPTY, null, false, false);
		}
		try {
			ParsedSavedValues parsed = parseSavedValues(IniFileReader.read(loadPath).lines());
			if (parsed.needsCorrection()) {
				LOGGER.error(
					"Malformed sort order config file '{}' will be backed up and corrected: {}",
					loadPath,
					summarizeDiagnostics(parsed.diagnostics())
				);
			}
			return new LoadedSavedValues(parsed.savedValues(), loadPath, parsed.needsCorrection(), false);
		} catch (IniFileReader.MalformedFileException e) {
			LOGGER.error("Malformed sort order config file '{}': {}", loadPath, e.getMessage());
			return new LoadedSavedValues(SavedValues.EMPTY, loadPath, true, false);
		} catch (IOException e) {
			LOGGER.error("Failed to load sort order config from file: {}", loadPath, e);
			return new LoadedSavedValues(SavedValues.EMPTY, loadPath, false, true);
		}
	}

	private static ParsedSavedValues parseSavedValues(List<String> lines) {
		List<String> visibleValues = new ArrayList<>();
		List<String> hiddenValues = new ArrayList<>();
		List<String> diagnostics = new ArrayList<>();
		Set<String> encounteredSections = new HashSet<>();
		List<String> currentSection = null;
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (VISIBLE_SECTION.equals(line)) {
				currentSection = visibleValues;
			} else if (HIDDEN_SECTION.equals(line)) {
				currentSection = hiddenValues;
			} else if (line.isBlank()) {
				continue;
			} else if (currentSection == null) {
				addDiagnostic(diagnostics, "Line %s appears before a valid section.".formatted(index + 1));
				continue;
			} else {
				if (line.startsWith(ENCODED_VALUE_PREFIX)) {
					IDeserializeResult<IniValue.Scalar> result = IniValueCodec.deserializeScalar(
						line.substring(ENCODED_VALUE_PREFIX.length())
					);
					IniValue.Scalar value = result.getResult().orElse(null);
					if (value != null) {
						currentSection.add(value.value());
					} else {
						addDiagnostic(
							diagnostics,
							"Line %s has an invalid encoded value: %s".formatted(
								index + 1,
								String.join("; ", result.getDiagnostics())
							)
						);
					}
					continue;
				}
				if (line.startsWith("\\")) {
					currentSection.add(line.substring(1));
					continue;
				}
				currentSection.add(line);
				continue;
			}
			if (!encounteredSections.add(line)) {
				addDiagnostic(diagnostics, "Line %s repeats section %s.".formatted(index + 1, line));
			}
		}
		if (!encounteredSections.contains(VISIBLE_SECTION)) {
			addDiagnostic(diagnostics, "Missing required section: " + VISIBLE_SECTION);
		}
		if (!encounteredSections.contains(HIDDEN_SECTION)) {
			addDiagnostic(diagnostics, "Missing required section: " + HIDDEN_SECTION);
		}
		return new ParsedSavedValues(
			new SavedValues(visibleValues, hiddenValues),
			!diagnostics.isEmpty(),
			diagnostics
		);
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
		String encoded = IniValueCodec.serializeScalar(value);
		if (encoded.equals(value)) {
			return value;
		}
		return ENCODED_VALUE_PREFIX + encoded;
	}

	private static void addDiagnostic(List<String> diagnostics, String diagnostic) {
		if (diagnostics.size() < MAX_LOGGED_PROBLEMS) {
			diagnostics.add(diagnostic);
		} else if (diagnostics.size() == MAX_LOGGED_PROBLEMS) {
			diagnostics.add("Further diagnostics were suppressed.");
		}
	}

	private static String summarizeDiagnostics(List<String> diagnostics) {
		List<String> displayed = diagnostics.stream()
			.limit(MAX_LOGGED_PROBLEMS)
			.toList();
		String summary = String.join("; ", displayed);
		if (diagnostics.size() > MAX_LOGGED_PROBLEMS) {
			return summary + "; further diagnostics were suppressed.";
		}
		return summary;
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

	private record LoadedSavedValues(
		SavedValues savedValues,
		@Nullable Path loadPath,
		boolean needsCorrection,
		boolean readFailed
	) {}

	private record ParsedSavedValues(
		SavedValues savedValues,
		boolean needsCorrection,
		List<String> diagnostics
	) {
		private ParsedSavedValues {
			diagnostics = List.copyOf(diagnostics);
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
