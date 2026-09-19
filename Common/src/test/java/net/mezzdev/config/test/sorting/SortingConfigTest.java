package net.mezzdev.config.test.sorting;

import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileReader;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.sorting.SortingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SortingConfigTest {
	private static final IConfigValueSerializer<Integer> INTEGER_SERIALIZER = new IConfigValueSerializer<>() {
		@Override
		public String serialize(Integer value) {
			return value.toString();
		}

		@Override
		public IDeserializeResult<Integer> deserialize(String string) {
			try {
				return IDeserializeResult.success(Integer.parseInt(string));
			} catch (NumberFormatException e) {
				return IDeserializeResult.failure("Expected an integer.");
			}
		}

		@Override
		public boolean isValid(Integer value) {
			return value != null;
		}

		@Override
		public String getValidValuesDescription() {
			return "Any integer";
		}
	};
	private static final IConfigValueSerializer<EquivalentValue> EQUIVALENT_VALUE_SERIALIZER = new IConfigValueSerializer<>() {
		@Override
		public String serialize(EquivalentValue value) {
			return value.serializedIdentity;
		}

		@Override
		public IDeserializeResult<EquivalentValue> deserialize(String string) {
			int separator = string.indexOf(':');
			if (separator < 1) {
				return IDeserializeResult.failure("Expected an id and variant.");
			}
			try {
				int id = Integer.parseInt(string.substring(0, separator));
				return IDeserializeResult.success(new EquivalentValue(id, string));
			} catch (NumberFormatException e) {
				return IDeserializeResult.failure("Expected an integer id.");
			}
		}

		@Override
		public boolean isValid(EquivalentValue value) {
			return value != null;
		}

		@Override
		public String getValidValuesDescription() {
			return "An id and variant";
		}
	};

	private static SortingConfig<String> createInMemorySortingConfig(
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return SortingConfig.inMemory(StringSerializer.INSTANCE, defaultSortOrder, allowsRemovingValues);
	}

	private static SortingConfig<String> createSortingConfig(
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return new SortingConfig<>(path, StringSerializer.INSTANCE, defaultSortOrder, allowsRemovingValues);
	}

	private static SortingConfig<String> createSortingConfig(
		Path defaultPath,
		Path path,
		Comparator<String> defaultSortOrder,
		boolean allowsRemovingValues
	) {
		return new SortingConfig<>(
			defaultPath,
			path,
			StringSerializer.INSTANCE,
			defaultSortOrder,
			allowsRemovingValues
		);
	}

	@Test
	public void inMemoryConfigRetainsChangesWithoutPersistence() {
		// Setup: a removable sorting config has no backing file.
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), true);

		// Operation: read defaults, then save a custom order that hides one value.
		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second")));

		// Assertions: the custom order and visibility remain available from memory.
		assertEquals(List.of("second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertFalse(sortingConfig.isVisible(List.of("first", "second"), "first"));
	}

	@Test
	public void concurrentUpdatesLeaveACompleteSortOrder() throws Exception {
		// Setup: two threads will replace the same initialized in-memory order at the same time.
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), false);
		List<String> allValues = List.of("first", "second", "third");
		List<String> firstOrder = List.of("second", "first", "third");
		List<String> secondOrder = List.of("third", "second", "first");
		assertEquals(allValues, sortingConfig.getSortedValues(allValues));
		AtomicInteger notifications = new AtomicInteger();
		sortingConfig.addChangeListener(notifications::incrementAndGet);
		CountDownLatch start = new CountDownLatch(1);

		// Operation: release both updates together and wait for each to complete.
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Boolean> firstUpdate = executor.submit(() -> {
				start.await();
				return sortingConfig.setSortedValues(allValues, firstOrder);
			});
			Future<Boolean> secondUpdate = executor.submit(() -> {
				start.await();
				return sortingConfig.setSortedValues(allValues, secondOrder);
			});
			start.countDown();

			assertTrue(firstUpdate.get(5, TimeUnit.SECONDS));
			assertTrue(secondUpdate.get(5, TimeUnit.SECONDS));
		}

		// Assertions: both committed updates notify, and final state is one complete submitted order.
		assertEquals(2, notifications.get());
		List<String> savedOrder = sortingConfig.getSortedValues(allValues);
		assertTrue(savedOrder.equals(firstOrder) || savedOrder.equals(secondOrder));
	}

	@Test
	public void missingValuesAreAppendedUsingDefaultOrder(@TempDir Path tempDir) throws IOException {
		// Setup: persisted state names only one of three current runtime values.
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "\\=second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		// Operation: reconcile the saved preference with the complete runtime collection.
		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		// Assertions: missing values append in default order and the reconciled order persists for reload.
		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals("[visible]", Files.readAllLines(path).getFirst());
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first", "third"), reloaded.getSortedValues(List.of("third", "first", "second")));
	}

	@Test
	public void removableConfigShowsNewRuntimeValues(@TempDir Path tempDir) {
		// Setup: a removable config has no explicit hidden values.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);

		// Operation: query once, then query again after a new runtime value appears.
		List<String> firstResult = sortingConfig.getSortedValues(List.of("a"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("a", "b"));

		// Assertions: previously unseen runtime values remain visible by default.
		assertEquals(List.of("a"), firstResult);
		assertEquals(List.of("a", "b"), secondResult);
	}

	@Test
	public void removableConfigPersistsHiddenValuesSeparatelyFromNewValues(@TempDir Path tempDir) {
		// Setup: a removable config starts with two visible values.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "b"), sortingConfig.getSortedValues(List.of("a", "b")));

		// Operation: hide one known value and later introduce another runtime value.
		assertTrue(sortingConfig.setSortedValues(List.of("a", "b"), List.of("a")));
		assertEquals(List.of("a", "c"), sortingConfig.getSortedValues(List.of("a", "b", "c")));
		assertFalse(sortingConfig.isVisible(List.of("a", "b", "c"), "b"));

		// Assertions: reload preserves explicit hiding while appending newly discovered values as visible.
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "c", "d"), reloaded.getSortedValues(List.of("a", "b", "c", "d")));
		assertFalse(reloaded.isVisible(List.of("a", "b", "c", "d"), "b"));
	}

	@Test
	public void savedPreferenceIsReconciledAgainstEveryRuntimeCollection(@TempDir Path tempDir) throws IOException {
		// Setup: persisted state prefers one value that appears in two different runtime collections.
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "\\=second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		// Operation: reconcile the saved preference against two successive complete collections.
		List<String> firstResult = sortingConfig.getSortedValues(List.of("third", "first", "second"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("fourth", "second"));

		// Assertions: each result is complete, persisted for reload, and exposed as an immutable snapshot.
		assertEquals(List.of("second", "first", "third"), firstResult);
		assertEquals(List.of("second", "fourth"), secondResult);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "fourth"), reloaded.getSortedValues(List.of("fourth", "second")));
		assertThrows(UnsupportedOperationException.class, () -> secondResult.add("fifth"));
	}

	@Test
	public void duplicatePersistedValuesAreReconciledAndRewritten(@TempDir Path tempDir) throws IOException {
		// Setup: persisted visible order repeats one serialized value.
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "\\=second", "\\=second", "\\=first", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		// Operation: reconcile the malformed saved order with current runtime values.
		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		// Assertions: duplicates collapse while missing values append, and corrected state is rewritten once.
		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals(1, Files.readAllLines(path).stream().filter("\\=second"::equals).count());
	}

	@Test
	public void setSortedValuesWritesFileAndNotifiesListeners(@TempDir Path tempDir) throws IOException {
		// Setup: a file-backed config has one listener and targets a missing parent directory.
		Path path = tempDir.resolve("nested").resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		// Operation: save a changed order and then submit the same order again.
		List<String> allValues = List.of("first", "third");
		boolean changed = sortingConfig.setSortedValues(allValues, List.of("third", "first"));
		boolean unchanged = sortingConfig.setSortedValues(allValues, List.of("third", "first"));

		// Assertions: only the real change writes durable state and notifies once.
		assertTrue(changed);
		assertFalse(unchanged);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("third", "first"), reloaded.getSortedValues(List.of("first", "third")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void changeListenerRemovalIsIdempotentForDuplicateRegistrations(@TempDir Path tempDir) {
		// Setup: the same change listener is registered twice, with the first unsubscribe callback retained.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		AtomicInteger notifications = new AtomicInteger();
		Runnable listener = notifications::incrementAndGet;
		Runnable unsubscribeFirst = sortingConfig.addChangeListener(listener);
		sortingConfig.addChangeListener(listener);

		// Operation: invoke the first callback repeatedly and then save a changed order.
		unsubscribeFirst.run();
		unsubscribeFirst.run();
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first")));

		// Assertions: one registration remains and duplicate unsubscription has no extra effect.
		assertEquals(1, notifications.get());
	}

	@Test
	public void oversizedSortOrderIsRejectedBeforeStateOrFileChanges(@TempDir Path tempDir) {
		// Setup: one serialized sorting value would make the file exceed its readable byte limit.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		String oversized = "x".repeat(ConfigFileReader.MAX_FILE_BYTES);

		// Operation: try to persist an order containing the oversized value.
		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("small", oversized), List.of(oversized, "small"))
		);

		// Assertions: validation prevents file creation and leaves subsequent in-memory state clean.
		assertFalse(Files.exists(path));
		assertEquals(List.of("small"), sortingConfig.getSortedValues(List.of("small")));
	}

	@Test
	public void setSortedValuesRejectsDuplicates(@TempDir Path tempDir) {
		// Setup: a file-backed config receives a proposed order with a duplicate value.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		// Operation: attempt to save the duplicate order.
		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("first"), List.of("first", "first"))
		);

		// Assertions: invalid order does not create a persistence file.
		assertFalse(Files.exists(path));
	}

	@Test
	public void setSortedValuesRejectsValuesOutsideTheCompleteValueSet(@TempDir Path tempDir) {
		// Setup: a proposed visible order names a value outside its declared complete runtime set.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);

		// Operation: attempt to save the inconsistent order.
		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("first"), List.of("second"))
		);

		// Assertions: invalid order does not create a persistence file.
		assertFalse(Files.exists(path));
	}

	@Test
	public void setSortedValuesUsesTheSuppliedCompleteValueSetWithoutAPriorRead() {
		// Setup: a fresh removable in-memory config has never loaded a runtime collection.
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), true);

		// Operation: save an order that hides one value from the supplied complete set.
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("first")));

		// Assertions: the hidden value remains excluded on the first read.
		assertEquals(List.of("first"), sortingConfig.getSortedValues(List.of("first", "second")));
	}

	@Test
	public void throwingListenerDoesNotPreventLaterListeners(@TempDir Path tempDir) throws IOException {
		// Setup: a failing change listener is registered before a recording listener.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> {
			throw new IllegalStateException("expected test failure");
		});
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		// Operation: save a changed order and dispatch both listeners.
		boolean changed = sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first"));

		// Assertions: persistence succeeds and the later listener still runs after the failure.
		assertTrue(changed);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first"), reloaded.getSortedValues(List.of("first", "second")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void persistedStateEscapesReservedAndBlankValues(@TempDir Path tempDir) {
		// Setup: runtime values include blank text, a reserved section header, and a leading escape character.
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		List<String> allValues = List.of("", "[hidden]", "\\value");
		assertEquals(allValues, sortingConfig.getSortedValues(allValues));

		// Operation: persist an order that hides the reserved-header value.
		assertTrue(sortingConfig.setSortedValues(allValues, List.of("", "\\value")));

		// Assertions: reload distinguishes escaped data from syntax and preserves hidden state.
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("", "\\value", "new"), reloaded.getSortedValues(List.of("", "[hidden]", "\\value", "new")));
		assertFalse(reloaded.isVisible(allValues, "[hidden]"));
	}

	@Test
	public void persistedStateRoundTripsDelimiterSensitiveStrings(@TempDir Path tempDir) {
		// Setup: sorting values cover empty, padded, punctuated, quoted, Unicode, and multiline strings.
		Path path = tempDir.resolve("sort-order.txt");
		List<String> values = List.of(
			"",
			" surrounding ",
			"a,b",
			"# = [punctuation]",
			"\"quoted\" \\ path",
			"こんにちは 🌍",
			"first line\nsecond line"
		);
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		sortingConfig.getSortedValues(values);

		// Operation: persist the sensitive values in their supplied order.
		assertTrue(sortingConfig.setSortedValues(values, values));

		// Assertions: a fresh config loads every value without changing text or order.
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(values, reloaded.getSortedValues(values));
	}

	@Test
	public void comparatorUsesSavedOrderAndDefaultOrderForUnknownValues(@TempDir Path tempDir) throws IOException {
		// Setup: persisted state prioritizes one value and natural order applies to values absent from it.
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "\\=second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		Comparator<String> comparator = sortingConfig.getComparator(List.of("third", "first", "second"));

		List<String> values = new ArrayList<>(List.of("third", "second", "first"));

		// Operation: sort through the config-provided comparator.
		values.sort(comparator);

		// Assertions: saved indexes win first, followed by unknown values in default order.
		assertEquals(List.of("second", "first", "third"), values);
	}

	@Test
	public void comparatorLooksUpSavedIndexesWithoutLinearScans() {
		// Setup: separate but equal collections contain one thousand values that count equality checks.
		AtomicInteger equalityChecks = new AtomicInteger();
		IConfigValueSerializer<CountingValue> serializer = new IConfigValueSerializer<>() {
			@Override
			public String serialize(CountingValue value) {
				return Integer.toString(value.id);
			}

			@Override
			public IDeserializeResult<CountingValue> deserialize(String string) {
				try {
					return IDeserializeResult.success(new CountingValue(Integer.parseInt(string), equalityChecks));
				} catch (NumberFormatException e) {
					return IDeserializeResult.failure("Expected an integer id");
				}
			}

			@Override
			public boolean isValid(CountingValue value) {
				return value != null;
			}

			@Override
			public String getValidValuesDescription() {
				return "Any integer id";
			}
		};
		SortingConfig<CountingValue> sortingConfig = SortingConfig.inMemory(
			serializer,
			Comparator.comparingInt(value -> value.id),
			false
		);
		List<CountingValue> registeredValues = new ArrayList<>();
		List<CountingValue> valuesToSort = new ArrayList<>();
		for (int id = 0; id < 1_000; id++) {
			registeredValues.add(new CountingValue(id, equalityChecks));
			valuesToSort.add(new CountingValue(id, equalityChecks));
		}
		Comparator<CountingValue> comparator = sortingConfig.getComparator(registeredValues);
		Collections.shuffle(valuesToSort, new Random(1));
		equalityChecks.set(0);

		// Operation: sort the shuffled values with the config comparator.
		valuesToSort.sort(comparator);

		// Assertions: indexed lookup stays well below quadratic comparisons and produces complete natural order.
		assertTrue(equalityChecks.get() < 50_000, "Comparator performed linear saved-order scans.");
		assertEquals(0, valuesToSort.getFirst().id);
		assertEquals(999, valuesToSort.getLast().id);
	}

	@Test
	public void layeredConfigGeneratesPackDefaultWithoutCreatingPlayerFile(@TempDir Path tempDir) throws IOException {
		// Setup: neither pack-default nor player sorting state exists for a layered config.
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		// Operation: request the initial sorted runtime values.
		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));

		// Assertions: natural order is written as a pack default without creating a player override.
		assertEquals(List.of("[visible]", "\\=first", "\\=second", "[hidden]"), Files.readAllLines(defaultPath));
		assertFalse(Files.exists(playerPath));
	}

	@Test
	public void layeredConfigLoadsPlayerOrderWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
		// Setup: pack and player layers contain different saved orders.
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		Files.write(defaultPath, List.of("[visible]", "\\=second", "\\=first", "[hidden]"));
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of("[visible]", "\\=first", "\\=second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		// Operation: load the player order and then reverse it through the public setter.
		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first")));

		// Assertions: updates write only the player layer and leave the pack default intact.
		assertEquals(List.of("[visible]", "\\=second", "\\=first", "[hidden]"), Files.readAllLines(defaultPath));
		assertEquals(List.of("[visible]", "\\=second", "\\=first", "[hidden]"), Files.readAllLines(playerPath));
	}

	@Test
	public void malformedPlayerFileIsBackedUpAndCorrectedWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
		// Setup: a valid pack default is overlaid by a malformed player order.
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		List<String> defaultContents = List.of("[visible]", "\\=second", "\\=first", "[hidden]");
		Files.write(defaultPath, defaultContents);
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of("[visible]", "\\=first", "\\=\"unterminated", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		// Operation: load and reconcile the layered sorting config.
		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("first", "second")));

		// Assertions: recovery backs up and corrects only the player layer.
		assertEquals(defaultContents, Files.readAllLines(defaultPath));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(playerPath, 1)));
		assertEquals(List.of("[visible]", "\\=first", "\\=second", "[hidden]"), Files.readAllLines(playerPath));
	}

	@Test
	public void malformedPackDefaultIsCorrectedWithoutCreatingPlayerFile(@TempDir Path tempDir) throws IOException {
		// Setup: the pack default repeats a visible value and no player file exists.
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		Files.write(defaultPath, List.of("[visible]", "\\=second", "\\=second", "\\=first", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		// Operation: load and reconcile the layered sorting config.
		assertEquals(List.of("second", "first"), sortingConfig.getSortedValues(List.of("first", "second")));

		// Assertions: recovery backs up and corrects the default without creating a player override.
		assertFalse(Files.exists(playerPath));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertEquals(List.of("[visible]", "\\=second", "\\=first", "[hidden]"), Files.readAllLines(defaultPath));
	}

	@Test
	public void transientReadFailureDoesNotReplaceOrBackUpPath(@TempDir Path tempDir) throws IOException {
		// Setup: the sorting path is temporarily a directory instead of a readable file.
		Path path = tempDir.resolve("sort-order.txt");
		Files.createDirectory(path);
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		// Operation: request sorted values while loading the path fails.
		assertEquals(List.of("first"), sortingConfig.getSortedValues(List.of("first")));

		// Assertions: transient I/O failure leaves the directory intact and creates no recovery backup.
		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void genericValuesPersistAndReconcileHiddenValues(@TempDir Path tempDir) {
		// Setup: a removable integer config starts in natural order.
		Path path = tempDir.resolve("integer-sort-order.txt");
		SortingConfig<Integer> sortingConfig = new SortingConfig<>(
			path,
			INTEGER_SERIALIZER,
			Comparator.naturalOrder(),
			true
		);
		assertEquals(List.of(1, 2, 3), sortingConfig.getSortedValues(List.of(3, 1, 2)));

		// Operation: persist a custom order that hides one integer.
		assertTrue(sortingConfig.setSortedValues(List.of(1, 2, 3), List.of(3, 1)));

		// Assertions: reload keeps the custom order and hiding while appending a new runtime value.
		SortingConfig<Integer> reloaded = new SortingConfig<>(
			path,
			INTEGER_SERIALIZER,
			Comparator.naturalOrder(),
			true
		);
		assertEquals(List.of(3, 1, 4), reloaded.getSortedValues(List.of(1, 2, 3, 4)));
		assertFalse(reloaded.isVisible(List.of(1, 2, 3, 4), 2));
	}

	@Test
	public void malformedGenericValuesAreSkippedAndCorrected(@TempDir Path tempDir) throws IOException {
		// Setup: persisted integer order mixes valid, invalid, unencoded, and hidden entries.
		Path path = tempDir.resolve("integer-sort-order.txt");
		Files.write(path, List.of(
			"[visible]",
			"\\=2",
			"\\=not-an-integer",
			"unencoded",
			"[hidden]",
			"\\=1"
		));
		SortingConfig<Integer> sortingConfig = new SortingConfig<>(
			path,
			INTEGER_SERIALIZER,
			Comparator.naturalOrder(),
			true
		);

		// Operation and assertions: loading keeps valid state, skips malformed values, and rewrites from a backup.
		assertEquals(List.of(2, 3), sortingConfig.getSortedValues(List.of(1, 2, 3)));
		assertFalse(sortingConfig.isVisible(List.of(1, 2, 3), 1));
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
		assertEquals(List.of("[visible]", "\\=2", "\\=3", "[hidden]", "\\=1"), Files.readAllLines(path));
	}

	@Test
	public void nonRoundTrippingSortingSerializerIsRejectedBeforeWriting(@TempDir Path tempDir) {
		// Setup: a serializer maps distinct integers to the same serialized identity.
		Path path = tempDir.resolve("ambiguous-sort-order.txt");
		IConfigValueSerializer<Integer> ambiguousSerializer = new IConfigValueSerializer<>() {
			@Override
			public String serialize(Integer value) {
				return Integer.toString(value % 2);
			}

			@Override
			public IDeserializeResult<Integer> deserialize(String string) {
				return IDeserializeResult.success(Integer.parseInt(string));
			}

			@Override
			public boolean isValid(Integer value) {
				return value != null;
			}

			@Override
			public String getValidValuesDescription() {
				return "Any integer";
			}
		};
		SortingConfig<Integer> sortingConfig = new SortingConfig<>(
			path,
			ambiguousSerializer,
			Comparator.naturalOrder(),
			false
		);

		// Operation: try to initialize sorting with colliding serialized values.
		assertThrows(IllegalArgumentException.class, () -> sortingConfig.getSortedValues(List.of(1, 3)));

		// Assertions: round-trip validation fails before a file is written.
		assertFalse(Files.exists(path));
	}

	@Test
	public void equalSortingValuesMustHaveOneSerializedIdentity(@TempDir Path tempDir) {
		// Setup: two values compare equal by ID but serialize with different variants.
		Path path = tempDir.resolve("inconsistent-sort-order.txt");
		SortingConfig<EquivalentValue> sortingConfig = new SortingConfig<>(
			path,
			EQUIVALENT_VALUE_SERIALIZER,
			Comparator.comparingInt(value -> value.id),
			false
		);
		EquivalentValue first = new EquivalentValue(1, "1:first");
		EquivalentValue second = new EquivalentValue(1, "1:second");

		// Operation: try to initialize sorting with inconsistent identities for equal values.
		assertThrows(IllegalArgumentException.class, () -> sortingConfig.getSortedValues(List.of(first, second)));

		// Assertions: identity validation fails before a file is written.
		assertFalse(Files.exists(path));
	}

	@Test
	public void unchangedSortingReadsReuseValidationAndLookupResults() {
		// Setup: warm a snapshot and count serializer and comparator work after it is built.
		CountingStringSerializer serializer = new CountingStringSerializer();
		AtomicInteger comparisons = new AtomicInteger();
		SortingConfig<String> sorting = SortingConfig.inMemory(serializer, (left, right) -> {
			comparisons.incrementAndGet();
			return left.compareTo(right);
		}, true);
		List<String> values = new ArrayList<>();
		for (int index = 0; index < 100; index++) {
			values.add("value" + index);
		}
		List<String> expected = sorting.getSortedValues(values);
		serializer.calls.set(0);
		comparisons.set(0);

		// Operation: rendering an unchanged list asks for order, a comparator, and each item's visibility.
		assertEquals(expected, sorting.getSortedValues(new ArrayList<>(values)));
		sorting.getComparator(values);
		for (String value : values) {
			assertTrue(sorting.isVisible(values, value));
		}

		// Assertions: validated values are neither serialized again nor re-sorted on those reads.
		assertEquals(0, serializer.calls.get());
		assertEquals(0, comparisons.get());
	}

	@Test
	public void sortingSnapshotDetectsChangesToTheSameInputCollection() {
		// Setup: callers may reuse a mutable collection between discoveries.
		SortingConfig<String> sorting = createInMemorySortingConfig(Comparator.naturalOrder(), true);
		List<String> values = new ArrayList<>(List.of("b", "a"));
		List<String> previous = sorting.getSortedValues(values);

		// Operation: replace a value without changing the collection's identity or size.
		values.set(1, "c");
		assertEquals(List.of("b", "c"), sorting.getSortedValues(values));
		assertFalse(sorting.isVisible(values, "a"));
		assertTrue(sorting.isVisible(values, "c"));
		assertEquals(List.of("a", "b"), previous);

		// Assertions: a cached snapshot cannot hide invalid new input either.
		values.set(1, null);
		assertThrows(IllegalArgumentException.class, () -> sorting.getSortedValues(values));
	}

	@Test
	public void sortingEditsRefreshSnapshotsBeforeNotifyingListeners() {
		// Setup: hold the previous list and comparator while warming visibility lookups.
		SortingConfig<String> sorting = createInMemorySortingConfig(Comparator.naturalOrder(), true);
		List<String> values = List.of("a", "b", "c");
		List<String> previous = sorting.getSortedValues(values);
		Comparator<String> previousComparator = sorting.getComparator(values);
		assertTrue(sorting.isVisible(values, "b"));
		AtomicInteger notifications = new AtomicInteger();
		sorting.addChangeListener(() -> {
			assertEquals(List.of("c", "a"), sorting.getSortedValues(values));
			assertFalse(sorting.isVisible(values, "b"));
			assertTrue(sorting.getComparator(values).compare("c", "a") < 0);
			notifications.incrementAndGet();
		});

		// Operation: reorder values and hide one, then read the resulting snapshot again.
		assertTrue(sorting.setSortedValues(values, List.of("c", "a")));
		assertEquals(List.of("c", "a"), sorting.getSortedValues(values));

		// Assertions: listeners see the new state while previously returned views keep their old order.
		assertEquals(1, notifications.get());
		assertEquals(List.of("a", "b", "c"), previous);
		assertTrue(previousComparator.compare("a", "c") < 0);
	}

	@Test
	public void sortingSnapshotsFollowMigrationApplyAndRollback(@TempDir Path tempDir) {
		// Setup: a loaded order already has a cached snapshot when a migration prepares its update.
		SortingConfig<String> sorting = createSortingConfig(tempDir.resolve("sorting.txt"), Comparator.naturalOrder(), true);
		List<String> values = List.of("a", "b");
		assertEquals(values, sorting.getSortedValues(values));
		SortingConfig.MigrationUpdate<String> update = sorting.prepareMigrationUpdate(values, List.of("b"));

		// Operation and assertions: both applying and rolling back invalidate order and visibility lookups.
		update.apply();
		assertEquals(List.of("b"), sorting.getSortedValues(values));
		assertFalse(sorting.isVisible(values, "a"));
		update.rollback();
		assertEquals(values, sorting.getSortedValues(values));
		assertTrue(sorting.isVisible(values, "a"));
	}

	@Test
	public void cachedSortingInputsStillValidateEqualReplacementObjects(@TempDir Path tempDir) throws IOException {
		// Setup: equal objects can violate the serializer contract by carrying different serialized identities.
		Path path = tempDir.resolve("sorting.txt");
		SortingConfig<EquivalentValue> sorting = new SortingConfig<>(
			path, EQUIVALENT_VALUE_SERIALIZER, Comparator.comparingInt(value -> value.id), false
		);
		EquivalentValue first = new EquivalentValue(1, "1:first");
		EquivalentValue replacement = new EquivalentValue(1, "1:replacement");
		sorting.getSortedValues(List.of(first));
		String previousFile = Files.readString(path);

		// Operation and assertions: equality alone cannot bypass identity validation on a cached read.
		assertThrows(IllegalArgumentException.class, () -> sorting.getSortedValues(List.of(replacement)));
		assertEquals(previousFile, Files.readString(path));
		assertEquals(List.of(first), sorting.getSortedValues(List.of(first)));
	}

	@Test
	public void cachedSortingReadsRetryFailedWrites(@TempDir Path tempDir) throws IOException {
		// Setup: a file occupying the parent directory makes the initial automatic save fail.
		Path parent = tempDir.resolve("blocked");
		Files.writeString(parent, "not a directory");
		Path path = parent.resolve("sorting.txt");
		SortingConfig<String> sorting = createSortingConfig(path, Comparator.naturalOrder(), false);
		List<String> values = List.of("b", "a");
		assertEquals(List.of("a", "b"), sorting.getSortedValues(values));
		assertFalse(Files.exists(path));

		// Operation: restore the directory and read the same values again.
		Files.delete(parent);
		Files.createDirectory(parent);
		assertEquals(List.of("a", "b"), sorting.getSortedValues(values));

		// Assertions: caching does not suppress the pending write retry.
		assertTrue(Files.isRegularFile(path));
		assertEquals(List.of("a", "b"), createSortingConfig(path, Comparator.naturalOrder(), false).getSortedValues(values));
	}

	@Test
	public void cachedSortingReadsRecreateMissingDefaultFiles(@TempDir Path tempDir) throws IOException {
		// Setup: a layered config has loaded its generated default without creating a player override.
		Path defaults = tempDir.resolve("defaults.txt");
		Path player = tempDir.resolve("player.txt");
		SortingConfig<String> sorting = createSortingConfig(defaults, player, Comparator.naturalOrder(), false);
		List<String> values = List.of("b", "a");
		sorting.getSortedValues(values);
		Files.delete(defaults);

		// Operation and assertions: an unchanged cached read still repairs a missing default file.
		assertEquals(List.of("a", "b"), sorting.getSortedValues(values));
		assertTrue(Files.isRegularFile(defaults));
		assertFalse(Files.exists(player));
	}

	private static final class CountingStringSerializer implements IConfigValueSerializer<String> {
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public String serialize(String value) {
			calls.incrementAndGet();
			return StringSerializer.INSTANCE.serialize(value);
		}

		@Override
		public IDeserializeResult<String> deserialize(String string) {
			calls.incrementAndGet();
			return StringSerializer.INSTANCE.deserialize(string);
		}

		@Override
		public boolean isValid(String value) {
			calls.incrementAndGet();
			return StringSerializer.INSTANCE.isValid(value);
		}

		@Override
		public String getValidValuesDescription() {
			return StringSerializer.INSTANCE.getValidValuesDescription();
		}
	}

	private static final class EquivalentValue {
		private final int id;
		private final String serializedIdentity;

		private EquivalentValue(int id, String serializedIdentity) {
			this.id = id;
			this.serializedIdentity = serializedIdentity;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof EquivalentValue value && id == value.id;
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(id);
		}
	}

	private static final class CountingValue {
		private final int id;
		private final AtomicInteger equalityChecks;

		private CountingValue(int id, AtomicInteger equalityChecks) {
			this.id = id;
			this.equalityChecks = equalityChecks;
		}

		@Override
		public boolean equals(Object other) {
			equalityChecks.incrementAndGet();
			return other instanceof CountingValue value && id == value.id;
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(id);
		}
	}
}
