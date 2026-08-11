package net.mezzdev.config.test.sorting;

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
	public void missingValuesAreAppendedUsingDefaultOrder(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("second"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals(List.of("second", "first", "third"), Files.readAllLines(path));
	}

	@Test
	public void allowingRemovalKeepsOnlySavedVisibleValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("second", "missing"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), true);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second"), sortedValues);
		assertTrue(sortingConfig.isVisible(List.of("third", "first", "second"), "second"));
		assertFalse(sortingConfig.isVisible(List.of("third", "first", "second"), "first"));
		assertEquals(List.of("second"), Files.readAllLines(path));
	}

	@Test
	public void savedPreferenceIsReconciledAgainstEveryRuntimeCollection(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("second"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> firstResult = sortingConfig.getSortedValues(List.of("third", "first", "second"));
		List<String> secondResult = sortingConfig.getSortedValues(List.of("fourth", "second"));

		assertEquals(List.of("second", "first", "third"), firstResult);
		assertEquals(List.of("second", "fourth"), secondResult);
		assertEquals(List.of("second", "fourth"), Files.readAllLines(path));
		assertThrows(UnsupportedOperationException.class, () -> secondResult.add("fifth"));
	}

	@Test
	public void duplicatePersistedValuesAreReconciledAndRewritten(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("second", "second", "first"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);

		List<String> sortedValues = sortingConfig.getSortedValues(List.of("third", "first", "second"));

		assertEquals(List.of("second", "first", "third"), sortedValues);
		assertEquals(List.of("second", "first", "third"), Files.readAllLines(path));
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
		assertEquals(List.of("third", "first"), Files.readAllLines(path));
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
		assertEquals(List.of("second", "first"), Files.readAllLines(path));
		assertEquals(List.of("changed"), notifications);
	}

	@Test
	public void comparatorUsesSavedOrderAndDefaultOrderForUnknownValues(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("sort-order.txt");
		Files.write(path, List.of("second"));
		SortingConfig sortingConfig = new SortingConfig(path, Comparator.naturalOrder(), false);
		Comparator<String> comparator = sortingConfig.getComparator(List.of("third", "first", "second"));

		List<String> values = new ArrayList<>(List.of("third", "second", "first"));
		values.sort(comparator);

		assertEquals(List.of("second", "first", "third"), values);
	}
}
