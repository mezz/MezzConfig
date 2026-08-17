package net.mezzdev.config.test.sorting;

import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.sorting.SortingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SortingConfigTest {
	@Test
	public void inMemoryConfigRetainsChangesWithoutPersistence() {
		SortingConfig sortingConfig = SortingConfig.inMemory(Comparator.naturalOrder(), true);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("second", "first")));
		assertTrue(sortingConfig.setSortedValues(List.of("second")));
		assertEquals(List.of("second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertFalse(sortingConfig.isVisible(List.of("first", "second"), "first"));
	}

	@Test
	public void missingValuesAreAppendedUsingDefaultOrder(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals("[visible]", Files.readAllLines(path).getFirst());
		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first", "third"), reloaded.getSortedValues(List.of("third", "first", "second")));
	}

	@Test
	public void removableConfigShowsNewRuntimeValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);

		List<String> firstResult = sortingConfig.getSortedValues(List.of("a"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("a", "b"));

		assertEquals(List.of("a"), firstResult);
		assertEquals(List.of("a", "b"), secondResult);
	}

	@Test
	public void removableConfigPersistsHiddenValuesSeparatelyFromNewValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "b"), sortingConfig.getSortedValues(List.of("a", "b")));

		assertTrue(sortingConfig.setSortedValues(List.of("a")));
		assertEquals(List.of("a", "c"), sortingConfig.getSortedValues(List.of("a", "b", "c")));
		assertFalse(sortingConfig.isVisible(List.of("a", "b", "c"), "b"));

		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), true);
		assertEquals(List.of("a", "c", "d"), reloaded.getSortedValues(List.of("a", "b", "c", "d")));
		assertFalse(reloaded.isVisible(List.of("a", "b", "c", "d"), "b"));
	}

	@Test
	public void savedPreferenceIsReconciledAgainstEveryRuntimeCollection(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> firstResult = sortingConfig.getSortedValues(List.of("third", "first", "second"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("fourth", "second"));

		assertEquals(List.of("second", "first", "third"), firstResult);
		assertEquals(List.of("second", "fourth"), secondResult);
		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "fourth"), reloaded.getSortedValues(List.of("fourth", "second")));
		assertThrows(UnsupportedOperationException.class, () -> secondResult.add("fifth"));
	}

	@Test
	public void duplicatePersistedValuesAreReconciledAndRewritten(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "second", "first", "[hidden]"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals(1, Files.readAllLines(path).stream().filter("second"::equals).count());
	}

	@Test
	public void setSortedValuesWritesFileAndNotifiesListeners(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("nested").resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		boolean changed = sortingConfig.setSortedValues(List.of("third", "first"));
		boolean unchanged = sortingConfig.setSortedValues(List.of("third", "first"));

		assertTrue(changed);
		assertFalse(unchanged);
		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("third", "first"), reloaded.getSortedValues(List.of("first", "third")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void setSortedValuesRejectsDuplicates(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		assertThrows(
			IllegalArgumentException.class,
			() -> sortingConfig.setSortedValues(List.of("first", "first"))
		);
		assertFalse(Files.exists(path));
	}

	@Test
	public void throwingListenerDoesNotPreventLaterListeners(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);
		List<String> notifications = new ArrayList<>();
		sortingConfig.addChangeListener(() -> {
			throw new IllegalStateException("expected test failure");
		});
		sortingConfig.addChangeListener(() -> notifications.add("changed"));

		boolean changed = sortingConfig.setSortedValues(List.of("second", "first"));

		assertTrue(changed);
		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), false);
		assertEquals(List.of("second", "first"), reloaded.getSortedValues(List.of("first", "second")));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void persistedStateEscapesReservedAndBlankValues(@TempDir Path tempDir) {
		Path path = tempDir.resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);
		List<String> allValues = List.of("", "[hidden]", "\\value");
		assertEquals(allValues, sortingConfig.getSortedValues(allValues));

		assertTrue(sortingConfig.setSortedValues(List.of("", "\\value")));

		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), true);
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
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);
		sortingConfig.getSortedValues(values);

		assertTrue(sortingConfig.setSortedValues(values));

		SortingConfig reloaded = new SortingConfig(path, Comparator.naturalOrder(), true);
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
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);

		assertEquals(values, sortingConfig.getSortedValues(values));
	}

	@Test
	public void comparatorUsesSavedOrderAndDefaultOrderForUnknownValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("[visible]", "second", "[hidden]"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);
		Comparator<String> comparator = sortingConfig.getComparator(List.of("third", "first", "second"));

		List<String> values = new ArrayList<>(List.of("third", "second", "first"));
		values.sort(comparator);

		assertEquals(List.of("second", "first", "third"), values);
	}

	@Test
	public void layeredConfigGeneratesPackDefaultWithoutCreatingPlayerFile(@TempDir Path tempDir) throws IOException {
		Path defaultPath = tempDir.resolve("sort-order.txt");
		Path playerPath = tempDir.resolve("players").resolve("player-id").resolve("sort-order.txt");
		SortingConfig sortingConfig = new SortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

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
		SortingConfig sortingConfig = new SortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("first", "second"), sortingConfig.getSortedValues(List.of("first", "second")));
		assertTrue(sortingConfig.setSortedValues(List.of("second", "first")));

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
		SortingConfig sortingConfig = new SortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

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
		SortingConfig sortingConfig = new SortingConfig(defaultPath, playerPath, Comparator.naturalOrder(), false);

		assertEquals(List.of("second", "first"), sortingConfig.getSortedValues(List.of("first", "second")));

		assertFalse(Files.exists(playerPath));
		assertTrue(Files.exists(ConfigFileUtil.getBackupPath(defaultPath, 1)));
		assertEquals(List.of("[visible]", "second", "first", "[hidden]"), Files.readAllLines(defaultPath));
	}

	@Test
	public void transientReadFailureDoesNotReplaceOrBackUpPath(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.createDirectory(path);
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		assertEquals(List.of("first"), sortingConfig.getSortedValues(List.of("first")));

		assertTrue(Files.isDirectory(path));
		assertFalse(Files.exists(ConfigFileUtil.getBackupPath(path, 1)));
	}
}
