package net.mezzdev.config.test.schema;

import net.mezzdev.config.api.value.ConfigValueUpdateType;
import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.value.ConfigValue;
import net.mezzdev.config.api.value.ConfigValueChange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigSchemaTest {
	@Test
	public void applyChangesReturnsMostExpensiveUpdateType() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> immediate = builder.addBoolean("immediate", false, ConfigValueUpdateType.IMMEDIATE);
		ConfigValue<Boolean> onApply = builder.addBoolean("onApply", false, ConfigValueUpdateType.ON_APPLY);
		ConfigValue<Boolean> restart = builder.addBoolean("restart", false, ConfigValueUpdateType.RESTART);
		ConfigSchema schema = createSchema(builder);

		ConfigValueUpdateType updateType = schema.applyChanges(List.of(
			new ConfigValueChange<>(immediate, true),
			new ConfigValueChange<>(onApply, true),
			new ConfigValueChange<>(restart, true)
		));

		assertEquals(ConfigValueUpdateType.RESTART, updateType);
	}

	@Test
	public void applyChangesNotifiesListenersAfterAllValuesChange() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("mezz_config.config.test", "category");
		ConfigValue<Boolean> first = builder.addBoolean("first", false, ConfigValueUpdateType.ON_APPLY);
		ConfigValue<Boolean> second = builder.addBoolean("second", false, ConfigValueUpdateType.ON_APPLY);
		ConfigSchema schema = createSchema(builder);
		List<Boolean> observedValues = new ArrayList<>();
		first.addListener(v -> observedValues.add(second.getValue()));
		second.addListener(v -> observedValues.add(first.getValue()));

		ConfigValueUpdateType updateType = schema.applyChanges(List.of(
			new ConfigValueChange<>(first, true),
			new ConfigValueChange<>(second, true)
		));

		assertEquals(ConfigValueUpdateType.ON_APPLY, updateType);
		assertEquals(List.of(false, true), observedValues);
	}

	private static ConfigSchema createSchema(ConfigCategoryBuilder builder) {
		return new ConfigSchema(
			Path.of("build/tmp/test/config-schema-test.ini"),
			"mezz_config.config.test",
			List.of(builder),
			List.of(),
			Map.of(),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}
}
