package net.mezzdev.config.test.value;

import net.mezzdev.config.api.value.IAppliedConfigValueChange;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigValueTest {
	@Test
	public void setRejectsInvalidValuesWithoutChangingOrNotifying() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(ignored -> notifications.incrementAndGet());

		assertFalse(value.set(11));

		assertEquals(5, value.getValue());
		assertEquals(0, notifications.get());
	}

	@Test
	public void setNotifiesListenerWithAppliedChange() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		List<String> changes = new ArrayList<>();
		value.addListener(change -> changes.add("%s -> %s".formatted(change.oldValue(), change.newValue())));

		assertTrue(value.set(7));

		assertEquals(List.of("5 -> 7"), changes);
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
			changes.add("%s: %s -> %s".formatted(change.configValue().getName(), change.oldValue(), change.newValue()));
		});

		assertTrue(value.set(7));

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
	public void clearListenersStopsFutureNotifications() {
		ConfigValue<Boolean> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"enabled",
			false,
			BooleanSerializer.INSTANCE
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(ignored -> notifications.incrementAndGet());

		value.clearListeners();
		assertTrue(value.set(true));

		assertEquals(0, notifications.get());
	}
}
