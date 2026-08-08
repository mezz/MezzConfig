package net.mezzdev.config.test.schema;

import net.mezzdev.config.schema.ConfigCategoryBuilder;
import net.mezzdev.config.schema.ConfigEditorCategoryBuilder;
import net.mezzdev.config.schema.ConfigSchema;
import net.mezzdev.config.schema.ConfigTranslationChecker;
import net.mezzdev.deduplicatingrunner.DelayedTaskScheduler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTranslationCheckerTest {
	private static final DelayedTaskScheduler NO_SAVE_SCHEDULER = (command, delay) -> CompletableFuture.completedFuture(null);

	@Test
	void findsMissingCategoryAndValueTranslations() {
		// Setup: build a schema with localization keys that are deliberately absent from the test language.
		ConfigCategoryBuilder category = new ConfigCategoryBuilder("missing.config", "general");
		category.addBoolean("enabled", true)
			.build();
		ConfigEditorCategoryBuilder advanced = new ConfigEditorCategoryBuilder("missing.config", "advanced");
		ConfigSchema schema = new ConfigSchema(
			Path.of("test.ini"),
			List.of(category),
			List.of(category, advanced),
			NO_SAVE_SCHEDULER
		);

		// Operation: inspect every localization key used by the schema and its editor categories.
		List<String> untranslatedKeys = ConfigTranslationChecker.getUntranslatedKeys(
			schema.getEditorCategories(),
			schema.getCategories()
		);

		// Assertions: both names and descriptions are reported once, in schema declaration order.
		assertEquals(List.of(
			"missing.config.general",
			"missing.config.general.description",
			"missing.config.advanced",
			"missing.config.advanced.description",
			"missing.config.general.enabled",
			"missing.config.general.enabled.description"
		), untranslatedKeys);
	}
}
