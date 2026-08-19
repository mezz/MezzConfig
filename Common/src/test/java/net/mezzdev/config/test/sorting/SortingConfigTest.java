package net.mezzdev.config.test.sorting;

import net.mezzdev.config.api.value.IConfigValueSerializer;
import net.mezzdev.config.api.value.IDeserializeResult;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.serializers.StringSerializer;
import net.mezzdev.config.sorting.SortingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), true);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second")));
		assertEquals(List.of("second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertFalse(sortingConfig.isVisible(List.of("first", "second"), "first"));
	}

	@Test
	public void concurrentUpdatesLeaveACompleteSortOrder() throws Exception {
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), false);
		List<String> allValues = List.of("first", "second", "third");
		List<String> firstOrder = List.of("second", "first", "third");
		List<String> secondOrder = List.of("third", "second", "first");
		assertEquals(allValues, sortingConfig.getSortedValues(allValues));
		AtomicInteger notifications = new AtomicInteger();
		sortingConfig.addChangeListener(notifications::incrementAndGet);
		CountDownLatch start = new CountDownLatch(1);

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

		assertEquals(2, notifications.get());
		List<String> savedOrder = sortingConfig.getSortedValues(allValues);
		assertTrue(savedOrder.equals(firstOrder) || savedOrder.equals(secondOrder));
	}

	@Test
	public void missingValuesAreAppendedUsingDefaultOrder(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals("[visible]", Files.readAllLines(path).getFirst());
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first", "third"), reloaded.getSortedValues(List.of("third", "first", "second")));
	}

	@Test
	public void removableConfigShowsNewRuntimeValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);

		List<String> firstResult = sortingConfig.getSortedValues(List.of("a"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("a", "b"));

		assertEquals(List.of("a"), firstResult);
		assertEquals(List.of("a", "b"), secondResult);
	}

	@Test
	public void removableConfigPersistsHiddenValuesSeparatelyFromNewValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "b"), sortingConfig.getSortedValues(List.of("a", "b")));

		assertTrue(sortingConfig.setSortedValues(List.of("a", "b"), List.of("a")));
		assertEquals(List.of("a", "c"), sortingConfig.getSortedValues(List.of("a", "b", "c")));
		assertFalse(sortingConfig.isVisible(List.of("a", "b", "c"), "b"));

		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "c", "d"), reloaded.getSortedValues(List.of("a", "b", "c", "d")));
		assertFalse(reloaded.isVisible(List.of("a", "b", "c", "d"), "b"));
	}

	@Test
	public void savedPreferenceIsReconciledAgainstEveryRuntimeCollection(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		List<String> firstResult = sortingConfig.getSortedValues(List.of("third", "first", "second"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("fourth", "second"));

		assertEquals(List.of("second", "first", "third"), firstResult);
		assertEquals(List.of("second", "fourth"), secondResult);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "fourth"), reloaded.getSortedValues(List.of("fourth", "second")));
		assertThrows(UnsupportedOperationException.class, () -> secondResult.add("fifth"));
	}

	@Test
	public void duplicatePersistedValuesAreReconciledAndRewritten(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "second", "first", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals(1, Files.readAllLines(path).stream().filter("second"::equals).count());
	}

	@Test
	public void setSortedValuesWritesFileAndNotifiesListeners(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("nested").resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		List<String> allValues = List.of("first", "third");
		boolean changed = sortingConfig.setSortedValues(allValues, List.of("third", "first"));
		boolean unchanged = sortingConfig.setSortedValues(allValues, List.of("third", "first"));

		assertTrue(changed);
		assertFalse(unchanged);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("third", "first"), reloaded.getSortedValues(List.of("first", "third")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void setSortedValuesRejectsDuplicates(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("first"), List.of("first", "first"))
		);
		assertFalse(Files.exists(path));
	}

	@Test
	public void setSortedValuesRejectsValuesOutsideTheCompleteValueSet(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);

		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("first"), List.of("second"))
		);
		assertFalse(Files.exists(path));
	}

	@Test
	public void setSortedValuesUsesTheSuppliedCompleteValueSetWithoutAPriorRead() {
		SortingConfig<String> sortingConfig = createInMemorySortingConfig(Comparator.naturalOrder(), true);

		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("first")));

		assertEquals(List.of("first"), sortingConfig.getSortedValues(List.of("first", "second")));
	}

	@Test
	public void throwingListenerDoesNotPreventLaterListeners(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> {
			throw new IllegalStateException("expected test failure");
		});
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		boolean changed = sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first"));

		assertTrue(changed);
		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first"), reloaded.getSortedValues(List.of("first", "second")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void persistedStateEscapesReservedAndBlankValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);
		List<String> allValues = List.of("", "[hidden]", "\\value");
		assertEquals(allValues, sortingConfig.getSortedValues(allValues));

		assertTrue(sortingConfig.setSortedValues(allValues, List.of("", "\\value")));

		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("", "\\value", "new"), reloaded.getSortedValues(List.of("", "[hidden]", "\\value", "new")));
		assertFalse(reloaded.isVisible(allValues, "[hidden]"));
	}

	@Test
	public void persistedStateRoundTripsDelimiterSensitiveStrings(@TempDir Path tempDir) {
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

		assertTrue(sortingConfig.setSortedValues(values, values));

		SortingConfig<String> reloaded = createSortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(values, reloaded.getSortedValues(values));
	}

	@Test
	public void legacyRawAndEscapedValuesRetainTheirOriginalMeaning(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of(
			"[visible]",
			"\"quoted\"",
			" surrounding ",
			"[other]",
			"\\[hidden]",
			"\\\\path",
			"[hidden]"
		));
		List<String> values = List.of("\"quoted\"", " surrounding ", "[other]", "[hidden]", "\\path");
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), true);

		assertEquals(values, sortingConfig.getSortedValues(values));
	}

	@Test
	public void comparatorUsesSavedOrderAndDefaultOrderForUnknownValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);
		Comparator<String> comparator = sortingConfig.getComparator(List.of("third", "first", "second"));

		List<String> values = new ArrayList<>(List.of("third", "second", "first"));
		values.sort(comparator);

		assertEquals(List.of("second", "first", "third"), values);
	}

	@Test
	public void layeredConfigGeneratesPackDefaultWithoutCreatingPlayerFile(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));

		assertEquals(List.of("[visible]", "first", "second", "[hidden]"), Files.readAllLines(defaultPath));
		assertFalse(Files.exists(playerPath));
	}

	@Test
	public void layeredConfigLoadsPlayerOrderWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		Files.write(defaultPath, List.of("[visible]", "second", "first", "[hidden]"));
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of("[visible]", "first", "second", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertTrue(sortingConfig.setSortedValues(List.of("first", "second"), List.of("second", "first")));

		assertEquals(List.of("[visible]", "second", "first", "[hidden]"), Files.readAllLines(defaultPath));
		assertEquals(List.of("[visible]", "second", "first", "[hidden]"), Files.readAllLines(playerPath));
	}

	@Test
	public void malformedPlayerFileIsBackedUpAndCorrectedWithoutChangingPackDefault(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		List<String> defaultContents = List.of("[visible]", "second", "first", "[hidden]");
		Files.write(defaultPath, defaultContents);
		Files.createDirectories(playerPath.getParent());
		Files.write(playerPath, List.of("[visible]", "first", "\\=\"unterminated", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("first", "second")));

		assertEquals(defaultContents, Files.readAllLines(defaultPath));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(playerPath, 1)));
		assertEquals(List.of("[visible]", "first", "second", "[hidden]"), Files.readAllLines(playerPath));
	}

	@Test
	public void malformedPackDefaultIsCorrectedWithoutCreatingPlayerFile(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		Files.write(defaultPath, List.of("[visible]", "second", "second", "first", "[hidden]"));
		SortingConfig<String> sortingConfig = createSortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("second", "first"), sortingConfig.getSortedValues(List.of("first", "second")));

		assertFalse(Files.exists(playerPath));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertEquals(List.of("[visible]", "second", "first", "[hidden]"), Files.readAllLines(defaultPath));
	}

	@Test
	public void transientReadFailureDoesNotReplaceOrBackUpPath(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.createDirectory(path);
		SortingConfig<String> sortingConfig = createSortingConfig(path, Comparator.naturalOrder(), false);

		assertEquals(List.of("first"), sortingConfig.getSortedValues(List.of("first")));

		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}

	@Test
	public void genericValuesPersistAndReconcileHiddenValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("integer-sort-order.txt");
		SortingConfig<Integer> sortingConfig = new SortingConfig<>(
			path,
			INTEGER_SERIALIZER,
			Comparator.naturalOrder(),
			true
		);
		assertEquals(List.of(1, 2, 3), sortingConfig.getSortedValues(List.of(3, 1, 2)));

		assertTrue(sortingConfig.setSortedValues(List.of(1, 2, 3), List.of(3, 1)));

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

		assertEquals(List.of(2, 3), sortingConfig.getSortedValues(List.of(1, 2, 3)));
		assertFalse(sortingConfig.isVisible(List.of(1, 2, 3), 1));
		assertTrue(Files.isRegularFile(ConfigFileUtil.getBackupPath(path, 1)));
		assertEquals(List.of("[visible]", "\\=2", "\\=3", "[hidden]", "\\=1"), Files.readAllLines(path));
	}

	@Test
	public void nonRoundTrippingSortingSerializerIsRejectedBeforeWriting(@TempDir Path tempDir) {
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

		assertThrows(IllegalArgumentException.class, () -> sortingConfig.getSortedValues(List.of(1, 3)));
		assertFalse(Files.exists(path));
	}

	@Test
	public void equalSortingValuesMustHaveOneSerializedIdentity(@TempDir Path tempDir) {
		Path path = tempDir.resolve("inconsistent-sort-order.txt");
		SortingConfig<EquivalentValue> sortingConfig = new SortingConfig<>(
			path,
			EQUIVALENT_VALUE_SERIALIZER,
			Comparator.comparingInt(value -> value.id),
			false
		);
		EquivalentValue first = new EquivalentValue(1, "1:first");
		EquivalentValue second = new EquivalentValue(1, "1:second");

		assertThrows(IllegalArgumentException.class, () -> sortingConfig.getSortedValues(List.of(first, second)));
		assertFalse(Files.exists(path));
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
}
