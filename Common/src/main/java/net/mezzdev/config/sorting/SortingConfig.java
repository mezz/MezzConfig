package net.mezzdev.config.sorting;

import com.google.gson.JsonElement;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileReader;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.file.ConfigFileValueAdapter;
import net.mezzdev.config.file.ConfigFileValueCodec;
import net.mezzdev.config.util.ListenerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SortingConfig<T> implements ISortingConfig<T> {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String VISIBLE_SECTION = "[visible]";
	private static final String HIDDEN_SECTION = "[hidden]";
	private static final String ENCODED_VALUE_PREFIX = "\\=";
	private static final int MAX_LOGGED_PROBLEMS = 100;

	private final @Nullable Path defaultPath;
	private final @Nullable Path path;
	private final IConfigValueSerializer<T> serializer;
	private final Comparator<T> defaultSortOrder;
	private final boolean allowsRemovingValues;
	private final ListenerList<Runnable> changeListeners = new ListenerList<>();
	@Nullable
	private SavedValues<T> savedValues;
	private boolean savedValuesNeedWrite;
	private boolean writesBlockedByReadFailure;
	private @Nullable Path correctionPath;

	public SortingConfig(
		Path path,
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this.defaultPath = null;
		this.path = Objects.requireNonNull(path, "path");
		this.serializer = Objects.requireNonNull(serializer, "serializer");
		this.defaultSortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
		this.allowsRemovingValues = allowsRemovingValues;
	}

	public SortingConfig(
		Path defaultPath,
		Path path,
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this.defaultPath = Objects.requireNonNull(defaultPath, "defaultPath");
		this.path = Objects.requireNonNull(path, "path");
		this.serializer = Objects.requireNonNull(serializer, "serializer");
		this.defaultSortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
		this.allowsRemovingValues = allowsRemovingValues;
	}

	public static <T> SortingConfig<T> inMemory(
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return new SortingConfig<>(serializer, defaultSortOrder, allowsRemovingValues);
	}

	private SortingConfig(
		IConfigValueSerializer<T> serializer,
		Comparator<T> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		this.defaultPath = null;
		this.path = null;
		this.serializer = Objects.requireNonNull(serializer, "serializer");
		this.defaultSortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
		this.allowsRemovingValues = allowsRemovingValues;
	}

	@Override
	public synchronized List<T> getSortedValues(Collection<T> allValues) {
		List<T> allValuesSnapshot = getDistinctValues(allValues, "allValues");
		writeDefaultIfMissing(allValuesSnapshot);
		SavedValues<T> previousSavedValues = getSavedValues();
		validateSerializedIdentities(previousSavedValues, allValuesSnapshot);
		SavedValues<T> reconciledSavedValues = addDiscoveredValues(previousSavedValues, allValuesSnapshot);
		if (savedValuesNeedWrite || !previousSavedValues.equals(reconciledSavedValues)) {
			validateSavedValuesForWrite(reconciledSavedValues);
			this.savedValues = reconciledSavedValues;
			this.savedValuesNeedWrite = !save(reconciledSavedValues);
		}
		return getCurrentVisibleValues(reconciledSavedValues, allValuesSnapshot);
	}

	@Override
	public synchronized List<T> getDefaultSortedValues(Collection<T> allValues) {
		List<T> allValuesSnapshot = getDistinctValues(allValues, "allValues");
		return allValuesSnapshot.stream()
			.sorted(defaultSortOrder)
			.toList();
	}

	@Override
	public synchronized boolean setSortedValues(Collection<T> allValues, List<T> sortedValues) {
		List<T> allValuesSnapshot = getDistinctValues(allValues, "allValues");
		Objects.requireNonNull(sortedValues, "sortedValues");
		List<T> sortedValuesCopy = copySortedValues(sortedValues);
		if (!new HashSet<>(allValuesSnapshot).containsAll(sortedValuesCopy)) {
			throw new IllegalArgumentException("sortedValues must only contain values from allValues.");
		}
		SavedValues<T> previousSavedValues = getSavedValues();
		validateSerializedIdentities(previousSavedValues, sortedValuesCopy, allValuesSnapshot);
		SavedValues<T> updatedSavedValues = updateSavedValues(previousSavedValues, allValuesSnapshot, sortedValuesCopy);
		boolean changed = !previousSavedValues.equals(updatedSavedValues);
		if (savedValuesNeedWrite || changed) {
			validateSavedValuesForWrite(updatedSavedValues);
			this.savedValues = updatedSavedValues;
			this.savedValuesNeedWrite = !save(updatedSavedValues);
		}
		if (!changed) {
			return false;
		}
		notifyListeners();
		return true;
	}

	private SavedValues<T> addDiscoveredValues(SavedValues<T> savedValues, List<T> allValues) {
		Set<T> knownValues = getKnownValues(savedValues);
		List<T> discoveredValues = allValues.stream()
			.filter(knownValues::add)
			.sorted(defaultSortOrder)
			.toList();
		if (discoveredValues.isEmpty()) {
			return savedValues;
		}
		List<T> visibleValues = new ArrayList<>(savedValues.visibleValues());
		visibleValues.addAll(discoveredValues);
		return new SavedValues<>(visibleValues, savedValues.hiddenValues());
	}

	private SavedValues<T> updateSavedValues(SavedValues<T> savedValues, List<T> allValues, List<T> sortedValues) {
		Set<T> sortedValuesSet = new HashSet<>(sortedValues);
		Set<T> currentValues = new HashSet<>(allValues);
		List<T> visibleValues = new ArrayList<>(sortedValues);
		for (T previouslyVisible : savedValues.visibleValues()) {
			if (!sortedValuesSet.contains(previouslyVisible) && !currentValues.contains(previouslyVisible)) {
				visibleValues.add(previouslyVisible);
			}
		}

		if (!allowsRemovingValues) {
			Set<T> visibleValuesSet = new HashSet<>(visibleValues);
			List<T> requiredVisibleValues = new ArrayList<>();
			for (T value : allValues) {
				if (visibleValuesSet.add(value)) {
					requiredVisibleValues.add(value);
				}
			}
			for (T value : savedValues.hiddenValues()) {
				if (visibleValuesSet.add(value)) {
					requiredVisibleValues.add(value);
				}
			}
			requiredVisibleValues.sort(defaultSortOrder);
			visibleValues.addAll(requiredVisibleValues);
			return new SavedValues<>(visibleValues, List.of());
		}

		Set<T> hiddenValues = new LinkedHashSet<>(savedValues.hiddenValues());
		hiddenValues.removeAll(sortedValuesSet);
		allValues.stream()
			.filter(value -> !sortedValuesSet.contains(value))
			.sorted(defaultSortOrder)
			.forEach(hiddenValues::add);
		return new SavedValues<>(visibleValues, List.copyOf(hiddenValues));
	}

	private static <T> List<T> getCurrentVisibleValues(SavedValues<T> savedValues, List<T> allValues) {
		Set<T> currentValues = new HashSet<>(allValues);
		return savedValues.visibleValues().stream()
			.filter(currentValues::contains)
			.toList();
	}

	private static <T> Set<T> getKnownValues(SavedValues<T> savedValues) {
		Set<T> knownValues = new HashSet<>(savedValues.visibleValues());
		knownValues.addAll(savedValues.hiddenValues());
		return knownValues;
	}

	private static <T> List<T> copySortedValues(List<T> sortedValues) {
		final List<T> copy;
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

	private List<T> getDistinctValues(Collection<T> values, String parameterName) {
		Objects.requireNonNull(values, parameterName);
		List<T> valuesSnapshot = new ArrayList<>(values.size());
		for (T value : values) {
			if (value == null) {
				throw new IllegalArgumentException(parameterName + " must not contain null values.");
			}
			valuesSnapshot.add(value);
		}
		validateSerializedIdentities(List.of(valuesSnapshot));
		return List.copyOf(new LinkedHashSet<>(valuesSnapshot));
	}

	private boolean save(SavedValues<T> savedValues) {
		Path path = this.path;
		if (path == null) {
			return true;
		}
		if (writesBlockedByReadFailure) {
			return false;
		}
		Path savePath = correctionPath;
		if (savePath == null) {
			savePath = path;
		}
		try {
			if (correctionPath != null) {
				ConfigFileUtil.backUpFile(savePath);
			}
			write(savePath, savedValues);
			correctionPath = null;
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to save sort order config to file {}", savePath, e);
			return false;
		}
	}

	private void write(Path path, SavedValues<T> savedValues) throws IOException {
		List<String> serialized = serialize(savedValues);
		ConfigFileUtil.writeUsingTempFile(path, serialized);
	}

	private List<String> serialize(SavedValues<T> savedValues) {
		List<String> serialized = new ArrayList<>();
		serialized.add(VISIBLE_SECTION);
		savedValues.visibleValues().stream()
			.map(this::encodeValue)
			.forEach(serialized::add);
		serialized.add(HIDDEN_SECTION);
		savedValues.hiddenValues().stream()
			.map(this::encodeValue)
			.forEach(serialized::add);
		return List.copyOf(serialized);
	}

	private void validateSavedValuesForWrite(SavedValues<T> savedValues) {
		if (path != null) {
			ConfigFileUtil.validateReadableContents(serialize(savedValues));
		}
	}

	private void writeDefaultIfMissing(List<T> allValues) {
		Path defaultPath = this.defaultPath;
		if (defaultPath == null || Files.exists(defaultPath)) {
			return;
		}
		SavedValues<T> defaultValues = new SavedValues<>(
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

	private SavedValues<T> getSavedValues() {
		SavedValues<T> savedValues = this.savedValues;
		if (savedValues == null) {
			LoadedSavedValues<T> loaded = loadSavedValuesFromFile();
			SavedValues<T> loadedSavedValues = loaded.savedValues();
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

	private LoadedSavedValues<T> loadSavedValuesFromFile() {
		Path loadPath = path;
		if (loadPath == null) {
			return new LoadedSavedValues<>(SavedValues.empty(), null, false, false);
		}
		if (!Files.exists(loadPath)) {
			loadPath = defaultPath;
		}
		if (loadPath == null || !Files.exists(loadPath)) {
			return new LoadedSavedValues<>(SavedValues.empty(), null, false, false);
		}
		try {
			ParsedSavedValues<T> parsed = parseSavedValues(ConfigFileReader.read(loadPath).lines());
			if (parsed.needsCorrection()) {
				LOGGER.error(
					"Malformed sort order config file '{}' will be backed up and corrected: {}",
					loadPath,
					summarizeDiagnostics(parsed.diagnostics())
				);
			}
			return new LoadedSavedValues<>(parsed.savedValues(), loadPath, parsed.needsCorrection(), false);
		} catch (ConfigFileReader.MalformedFileException e) {
			LOGGER.error("Malformed sort order config file '{}': {}", loadPath, e.getMessage());
			return new LoadedSavedValues<>(SavedValues.empty(), loadPath, true, false);
		} catch (IOException e) {
			LOGGER.error("Failed to load sort order config from file: {}", loadPath, e);
			return new LoadedSavedValues<>(SavedValues.empty(), loadPath, false, true);
		}
	}

	private ParsedSavedValues<T> parseSavedValues(List<String> lines) {
		List<T> visibleValues = new ArrayList<>();
		List<T> hiddenValues = new ArrayList<>();
		List<String> diagnostics = new ArrayList<>();
		Set<String> encounteredSections = new HashSet<>();
		List<T> currentSection = null;
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
					IDeserializeResult<T> result = deserializeValue(
						line.substring(ENCODED_VALUE_PREFIX.length())
					);
					T value = result.getResult().orElse(null);
					if (value != null) {
						try {
							String canonicalLine = encodeValue(value);
							currentSection.add(value);
							if (!canonicalLine.equals(line)) {
								addDiagnostic(diagnostics, "Line %s uses a non-canonical value encoding.".formatted(index + 1));
							}
						} catch (IllegalArgumentException e) {
							addDiagnostic(
								diagnostics,
								"Line %s violates the sorting serializer contract: %s".formatted(index + 1, e.getMessage())
							);
						}
					}
					if (!result.getDiagnostics().isEmpty()) {
						addDiagnostic(
							diagnostics,
							"Line %s has an invalid serialized value: %s".formatted(
								index + 1,
								String.join("; ", result.getDiagnostics())
							)
						);
					}
					continue;
				}
				addDiagnostic(
					diagnostics,
					"Line %s must use an encoded serializer value beginning with %s."
						.formatted(index + 1, ENCODED_VALUE_PREFIX)
				);
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
		return new ParsedSavedValues<>(
			new SavedValues<>(visibleValues, hiddenValues),
			!diagnostics.isEmpty(),
			diagnostics
		);
	}

	private IDeserializeResult<T> deserializeValue(String serializedValue) {
		IDeserializeResult<JsonElement> decoded = ConfigFileValueCodec.deserialize(serializedValue);
		if (decoded.getResult().isEmpty()) {
			return IDeserializeResult.failure(decoded.getDiagnostics());
		}
		IDeserializeResult<T> deserialized = ConfigFileValueAdapter.deserialize(
			serializer,
			decoded.getResult().orElseThrow()
		);
		List<String> diagnostics = new ArrayList<>(decoded.getDiagnostics());
		diagnostics.addAll(deserialized.getDiagnostics());
		T value = deserialized.getResult().orElse(null);
		if (value == null) {
			return IDeserializeResult.failure(diagnostics);
		}
		if (diagnostics.isEmpty()) {
			return IDeserializeResult.success(value);
		}
		return IDeserializeResult.partialSuccess(value, diagnostics);
	}

	private SavedValues<T> normalizeSavedValues(SavedValues<T> savedValues) {
		Set<T> visibleValues = new LinkedHashSet<>(savedValues.visibleValues());
		Set<T> hiddenValues = new LinkedHashSet<>(savedValues.hiddenValues());
		hiddenValues.removeAll(visibleValues);
		if (!allowsRemovingValues && !hiddenValues.isEmpty()) {
			List<T> previouslyHiddenValues = new ArrayList<>(hiddenValues);
			previouslyHiddenValues.sort(defaultSortOrder);
			visibleValues.addAll(previouslyHiddenValues);
			hiddenValues.clear();
		}
		return new SavedValues<>(List.copyOf(visibleValues), List.copyOf(hiddenValues));
	}

	private String encodeValue(T value) {
		String encoded = getSerializedIdentity(value);
		return ENCODED_VALUE_PREFIX + encoded;
	}

	private String getSerializedIdentity(T value) {
		ConfigFileValueAdapter.validateRoundTrip(serializer, value);
		return ConfigFileValueAdapter.serialize(serializer, value);
	}

	private void validateSerializedIdentities(SavedValues<T> savedValues, Collection<T> values) {
		validateSerializedIdentities(List.of(savedValues.visibleValues(), savedValues.hiddenValues(), values));
	}

	private void validateSerializedIdentities(
		SavedValues<T> savedValues,
		Collection<T> firstValues,
		Collection<T> secondValues
	) {
		validateSerializedIdentities(List.of(
			savedValues.visibleValues(),
			savedValues.hiddenValues(),
			firstValues,
			secondValues
		));
	}

	private void validateSerializedIdentities(List<? extends Collection<T>> valueGroups) {
		Map<T, String> identitiesByValue = new HashMap<>();
		Map<String, T> valuesByIdentity = new HashMap<>();
		for (Collection<T> values : valueGroups) {
			for (T value : values) {
				String identity = getSerializedIdentity(value);
				String previousIdentity = identitiesByValue.putIfAbsent(value, identity);
				if (previousIdentity != null && !previousIdentity.equals(identity)) {
					throw new IllegalArgumentException("Equal sorting values must have the same serialized identity.");
				}
				T previousValue = valuesByIdentity.putIfAbsent(identity, value);
				if (previousValue != null && !previousValue.equals(value)) {
					throw new IllegalArgumentException("Unequal sorting values must not share a serialized identity.");
				}
			}
		}
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

	@Override
	public synchronized Comparator<T> getComparator(Collection<T> allValues) {
		List<T> sortedValues = getSortedValues(allValues);
		Map<T, Integer> savedIndexes = new HashMap<>();
		for (int index = 0; index < sortedValues.size(); index++) {
			savedIndexes.put(sortedValues.get(index), index);
		}
		Comparator<T> savedOrder = Comparator.comparingInt(value -> savedIndexes.getOrDefault(value, Integer.MAX_VALUE));
		return savedOrder.thenComparing(defaultSortOrder);
	}

	@Override
	public synchronized boolean isVisible(Collection<T> allValues, T value) {
		Objects.requireNonNull(value, "value");
		validateSerializedIdentities(List.of(List.of(value)));
		return getSortedValues(allValues).contains(value);
	}

	@Override
	public boolean allowsRemovingValues() {
		return allowsRemovingValues;
	}

	@Override
	public Runnable addChangeListener(Runnable listener) {
		Objects.requireNonNull(listener, "listener");
		return this.changeListeners.add(listener);
	}

	private void notifyListeners() {
		for (Runnable listener : changeListeners.snapshot()) {
			try {
				listener.run();
			} catch (RuntimeException e) {
				LOGGER.error("Sort order config listener failed for {}.", path, e);
			}
		}
	}

	private record LoadedSavedValues<T>(
		SavedValues<T> savedValues,
		@Nullable Path loadPath,
		boolean needsCorrection,
		boolean readFailed
	) {}

	private record ParsedSavedValues<T>(
		SavedValues<T> savedValues,
		boolean needsCorrection,
		List<String> diagnostics
	) {
		private ParsedSavedValues {
			diagnostics = List.copyOf(diagnostics);
		}
	}

	private record SavedValues<T>(
		List<T> visibleValues,
		List<T> hiddenValues
	) {
		private static <T> SavedValues<T> empty() {
			return new SavedValues<>(List.of(), List.of());
		}

		private SavedValues {
			visibleValues = List.copyOf(visibleValues);
			hiddenValues = List.copyOf(hiddenValues);
		}
	}

}
