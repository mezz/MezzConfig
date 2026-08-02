package net.mezzdev.config.test.value;

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
		value.addListener(v -> notifications.incrementAndGet());

		assertFalse(value.set(11));

		assertEquals(5, value.getValue());
		assertEquals(0, notifications.get());
	}

	@Test
	public void setFromSerializedValueDoesNotNotifyWhenValueIsEqual() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			1000,
			new IntegerSerializer(0, 2000)
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(v -> notifications.incrementAndGet());

		List<String> errors = value.setFromSerializedValue("1000");

		assertEquals(List.of(), errors);
		assertEquals(1000, value.getValue());
		assertEquals(0, notifications.get());
	}

	@Test
	public void setFromSerializedValueNotifiesWhenValueChanges() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			1000,
			new IntegerSerializer(0, 2000)
		);
		AtomicInteger notifications = new AtomicInteger();
		value.addListener(v -> notifications.incrementAndGet());

		List<String> errors = value.setFromSerializedValue("1001");

		assertEquals(List.of(), errors);
		assertEquals(1001, value.getValue());
		assertEquals(1, notifications.get());
	}

	@Test
	public void setNotifiesListenerWithOldAndNewValues() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			5,
			new IntegerSerializer(0, 10)
		);
		List<String> changes = new ArrayList<>();
		value.addListener((oldValue, newValue) -> changes.add("%s -> %s".formatted(oldValue, newValue)));

		assertTrue(value.set(7));

		assertEquals(List.of("5 -> 7"), changes);
	}

	@Test
	public void setFromSerializedValueNotifiesListenerWithOldAndNewValues() {
		ConfigValue<Integer> value = new ConfigValue<>(
			"mezz_config.config.test.category",
			"count",
			1000,
			new IntegerSerializer(0, 2000)
		);
		List<String> changes = new ArrayList<>();
		value.addListener((oldValue, newValue) -> changes.add("%s -> %s".formatted(oldValue, newValue)));

		List<String> errors = value.setFromSerializedValue("1001");

		assertEquals(List.of(), errors);
		assertEquals(List.of("1000 -> 1001"), changes);
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
		value.addListener(v -> notifications.incrementAndGet());

		value.clearListeners();
		assertTrue(value.set(true));

		assertEquals(0, notifications.get());
	}
}
