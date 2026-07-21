package mezz.jei.test.config.file;

import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.file.ConfigCategoryBuilder;
import net.mezzdev.config.file.ConfigSchema;
import net.mezzdev.config.file.ConfigValue;
import net.mezzdev.config.ConfigValueChange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigSchemaTest {
	@Test
	public void applyChangesReturnsMostExpensiveUpdateType() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("jei.config.test", "category");
		ConfigValue<Boolean> immediate = builder.addBoolean("immediate", false, ConfigValueUpdateType.IMMEDIATE);
		ConfigValue<Boolean> onApply = builder.addBoolean("onApply", false, ConfigValueUpdateType.ON_APPLY);
		ConfigValue<Boolean> restartJei = builder.addBoolean("restartJei", false, ConfigValueUpdateType.RESTART_JEI);
		ConfigSchema schema = createSchema(builder);

		ConfigValueUpdateType updateType = schema.applyChanges(List.of(
			new ConfigValueChange<>(immediate, true),
			new ConfigValueChange<>(onApply, true),
			new ConfigValueChange<>(restartJei, true)
		));

		assertEquals(ConfigValueUpdateType.RESTART_JEI, updateType);
	}

	@Test
	public void applyChangesNotifiesListenersAfterAllValuesChange() {
		ConfigCategoryBuilder builder = new ConfigCategoryBuilder("jei.config.test", "category");
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
			List.of(builder),
			List.of(),
			(command, delay) -> CompletableFuture.completedFuture(null)
		);
	}
}
