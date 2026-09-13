package net.mezzdev.config.test.value;

import net.mezzdev.config.api.value.change.IAppliedConfigValueChange;
import net.mezzdev.config.api.value.editor.ConfigValueEditMode;
import net.mezzdev.config.api.value.editor.ConfigValueRestartRequirement;
import net.mezzdev.config.api.value.change.IConfigValueChangeListener;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigValueTest {
	@Test
	public void setThrowsForInvalidValuesWithoutChangingOrNotifying() {
		// Setup: a bounded value has one listener and currently holds a valid value.
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(ignored -> notifications.incrementAndGet());

		// Operation: try to set a value above the configured range.
		assertThrows(IllegalArgumentException.class, () -> value.set(11));

		// Assertions: invalid input leaves state unchanged and emits no notification.
		assertEquals(5, value.get());
		assertEquals(0, notifications.get());
	}

	@Test
	public void setReturnsFalseForValidUnchangedValues() {
		// Setup: a bounded value and listener already observe the requested value.
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(ignored -> notifications.incrementAndGet());

		// Operation: set the same valid value again.
		assertFalse(value.set(5));

		// Assertions: a no-op retains state and emits no notification.
		assertEquals(5, value.get());
		assertEquals(0, notifications.get());
	}

	@Test
	public void setNotifiesListenerWithAppliedChange() {
		// Setup: a listener records the old and new values of each applied change.
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		List<String> changes = new ArrayList<>();
		value.addListener(change -> changes.add("%s -> %s".formatted(change.oldValue(), change.newValue())));

		// Operation: set a different valid value.
		assertTrue(value.set(7));

		// Assertions: the listener receives the applied transition once.
		assertEquals(List.of("5 -> 7"), changes);
	}

	@Test
	public void restartRequiredSetNotifiesOnlyPendingListeners() {
		// Setup: a game-restart value has pending and effective listeners registered separately.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE,
			ConfigValueEditMode.BATCH,
			ConfigValueRestartRequirement.GAME_RESTART,
			List.of()
		);
		List<String> pendingChanges = new ArrayList<>();
		List<String> pendingBatches = new ArrayList<>();
		AtomicInteger effectiveNotifications = new AtomicInteger();
		value.addPendingListener(change -> pendingChanges.add("%s -> %s".formatted(change.oldValue(), change.newValue())));
		value.addPendingBatchListener(changes -> pendingBatches.add("batch: " + changes.size()));
		value.addListener(ignored -> effectiveNotifications.incrementAndGet());

		// Operation: select a new value before the required restart.
		assertTrue(value.set(true));

		// Assertions: pending state and listeners update while effective state and listeners remain unchanged.
		assertFalse(value.get());
		assertTrue(value.getEditorInfo().getPendingValue());
		assertEquals(List.of("false -> true"), pendingChanges);
		assertEquals(List.of("batch: 1"), pendingBatches);
		assertEquals(0, effectiveNotifications.get());
	}

	@Test
	public void addListenerReturnsUnsubscribeCallback() {
		// Setup: register a normal single-value listener and keep its unsubscribe callback.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		Runnable unsubscribe = value.addListener(ignored -> notifications.incrementAndGet());

		// Operation: notify once, unsubscribe, then change the value again.
		assertTrue(value.set(true));
		unsubscribe.run();
		assertTrue(value.set(false));

		// Assertions: the listener only receives changes before its unsubscribe callback is run.
		assertEquals(1, notifications.get());
	}

	@Test
	public void listenerCanUnsubscribeDuringNotification() {
		// Setup: register a listener that removes itself while handling its first change.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		AtomicReference<Runnable> unsubscribe = new AtomicReference<>();
		unsubscribe.set(value.addListener(ignored -> {
			notifications.incrementAndGet();
			unsubscribe.get().run();
		}));

		// Operation: apply two changes that would both notify if the listener remained subscribed.
		assertTrue(value.set(true));
		assertTrue(value.set(false));

		// Assertions: notification uses a stable listener snapshot and the self-unsubscribe prevents later callbacks.
		assertEquals(1, notifications.get());
	}

	@Test
	public void setNotifiesBatchListenersWithOneChange() {
		// Setup: a value-scoped batch listener records its single applied change.
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		List<String> changes = new ArrayList<>();
		value.addBatchListener(batch -> {
			assertEquals(1, batch.size());
			IAppliedConfigValueChange<?> change = batch.getFirst();
			changes.add("%s: %s -> %s".formatted(
				change.configValue().getEditorInfo().getName(),
				change.oldValue(),
				change.newValue()
			));
		});

		// Operation: set a different valid value directly.
		assertTrue(value.set(7));

		// Assertions: direct set is represented as one value-scoped batch.
		assertEquals(List.of("count: 5 -> 7"), changes);
	}

	@Test
	public void addBatchListenerReturnsUnsubscribeCallback() {
		// Setup: register a value-scoped batch listener and keep its unsubscribe callback.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		Runnable unsubscribe = value.addBatchListener(ignored -> notifications.incrementAndGet());

		// Operation: notify once, unsubscribe, then change the value again.
		assertTrue(value.set(true));
		unsubscribe.run();
		assertTrue(value.set(false));

		// Assertions: the batch listener only receives changes before its unsubscribe callback is run.
		assertEquals(1, notifications.get());
	}

	@Test
	public void unsubscribeOnlyRemovesTheOwningListener() {
		// Setup: two different listeners are registered and only the first one's callback is retained.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger removedNotifications = new AtomicInteger();
		AtomicInteger retainedNotifications = new AtomicInteger();
		Runnable unsubscribe = value.addListener(ignored -> removedNotifications.incrementAndGet());
		value.addListener(ignored -> retainedNotifications.incrementAndGet());

		// Operation: unsubscribe the first listener and change the value.
		unsubscribe.run();
		assertTrue(value.set(true));

		// Assertions: the owning listener is removed while the unrelated listener remains active.
		assertEquals(0, removedNotifications.get());
		assertEquals(1, retainedNotifications.get());
	}

	@Test
	public void unsubscribeIsIdempotentForDuplicateListenerRegistrations() {
		// Setup: the same listener object is registered twice, with the first callback retained.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		IConfigValueChangeListener<Boolean> listener = ignored -> notifications.incrementAndGet();
		Runnable unsubscribeFirst = value.addListener(listener);
		value.addListener(listener);

		// Operation: invoke the first callback repeatedly and then change the value.
		unsubscribeFirst.run();
		unsubscribeFirst.run();
		assertTrue(value.set(true));

		// Assertions: one registration remains and duplicate unsubscription has no extra effect.
		assertEquals(1, notifications.get());
	}

	@Test
	public void listenerFailuresDoNotPreventLaterListeners() {
		// Setup: failing and successful listeners are interleaved across single and batch notifications.
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(ignored -> {
			throw new IllegalStateException("expected test failure");
		});
		value.addListener(ignored -> notifications.incrementAndGet());
		value.addBatchListener(ignored -> {
			throw new IllegalStateException("expected batch test failure");
		});
		value.addBatchListener(ignored -> notifications.incrementAndGet());

		// Operation: change the value and dispatch all listeners.
		assertTrue(value.set(true));

		// Assertions: both successful listeners still run after earlier failures.
		assertEquals(2, notifications.get());
	}
}
