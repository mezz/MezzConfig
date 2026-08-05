package net.mezzdev.config.test.value;

import net.mezzdev.config.api.value.IAppliedConfigValueChange;
import net.mezzdev.config.serializers.BooleanSerializer;
import net.mezzdev.config.serializers.IntegerSerializer;
import net.mezzdev.config.value.ConfigValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
