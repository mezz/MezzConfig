package net.mezzdev.config;

import net.mezzdev.config.file.ConfigSchemaBuilder;
import net.mezzdev.config.file.ConfigDisplayCategory;
import net.mezzdev.config.file.FileWatcher;
import net.mezzdev.config.file.IConfigSaveScheduler;
import net.mezzdev.config.file.IConfigSchema;
import net.mezzdev.config.file.IConfigSchemaBuilder;

import java.nio.file.Path;
import java.util.List;

public class JeiClientConfigs implements IJeiClientConfigs {
	private final IClientConfig clientConfig;
	private final IIngredientFilterConfig ingredientFilterConfig;
	private final IIngredientGridConfig ingredientListConfig;
	private final IIngredientGridConfig bookmarkListConfig;

	private final IConfigSchema schema;

	public JeiClientConfigs(Path configFile, boolean isDev, IConfigSaveScheduler scheduler) {
		IConfigSchemaBuilder builder = new ConfigSchemaBuilder(configFile, "jei.config.client", scheduler);

		ClientConfig clientConfig = new ClientConfig(builder, isDev);
		IngredientFilterConfig ingredientFilterConfig = new IngredientFilterConfig(builder);
		IngredientGridConfig ingredientListConfig = new IngredientGridConfig("ingredientList", builder, HorizontalConfigAlignment.RIGHT);
		IngredientGridConfig bookmarkListConfig = new IngredientGridConfig("bookmarkList", builder, HorizontalConfigAlignment.LEFT);
		addDisplayCategories(builder, clientConfig, ingredientFilterConfig, ingredientListConfig, bookmarkListConfig);

		this.clientConfig = clientConfig;
		this.ingredientFilterConfig = ingredientFilterConfig;
		this.ingredientListConfig = ingredientListConfig;
		this.bookmarkListConfig = bookmarkListConfig;
		schema = builder.build();
	}

	private static void addDisplayCategories(
		IConfigSchemaBuilder builder,
		IClientConfig clientConfig,
		IIngredientFilterConfig ingredientFilterConfig,
		IngredientGridConfig ingredientListConfig,
		IngredientGridConfig bookmarkListConfig
	) {
		builder.addDisplayCategory(category("search", List.of(
			ingredientFilterConfig.modNameSearchMode(),
			ingredientFilterConfig.tagSearchMode(),
			ingredientFilterConfig.tooltipSearchMode(),
			ingredientFilterConfig.colorSearchMode(),
			ingredientFilterConfig.resourceLocationSearchMode(),
			ingredientFilterConfig.creativeTabSearchMode(),
			ingredientFilterConfig.searchAdvancedTooltips(),
			ingredientFilterConfig.searchModIds(),
			ingredientFilterConfig.searchModAliases(),
			ingredientFilterConfig.searchShortModNames(),
			ingredientFilterConfig.searchIngredientAliases(),
			clientConfig.searchBarPosition()
		)));
		builder.addDisplayCategory(category("ingredientList", List.of(
			ingredientListConfig.maxRows(),
			ingredientListConfig.maxColumns(),
			ingredientListConfig.alignment(),
			ingredientListConfig.buttonNavigationVisibility(),
			ingredientListConfig.drawBackground(),
			clientConfig.ingredientSorterStages(),
			clientConfig.toastReflowEnabled()
		)));
		builder.addDisplayCategory(category("bookmarkList", List.of(
			bookmarkListConfig.maxRows(),
			bookmarkListConfig.maxColumns(),
			bookmarkListConfig.alignment(),
			bookmarkListConfig.buttonNavigationVisibility(),
			bookmarkListConfig.drawBackground(),
			clientConfig.bookmarkAddPosition(),
			clientConfig.dragToRearrangeBookmarksEnabled(),
			clientConfig.bookmarkTooltipPreviewEnabled(),
			clientConfig.bookmarkTooltipIngredientsEnabled(),
			clientConfig.holdShiftToShowBookmarkTooltipFeaturesEnabled()
		)));
		builder.addDisplayCategory(category("input", ConfigDisplayCategoryRole.INPUT, List.of(
			clientConfig.dragDelayMs(),
			clientConfig.smoothScrollRate()
		)));
		builder.addDisplayCategory(category("recipes", List.of(
			clientConfig.showTagRecipesEnabled(),
			clientConfig.maxRecipeGuiHeight(),
			clientConfig.recipeSortingBookmarksEnabled(),
			clientConfig.recipeSortingCraftableEnabled(),
			clientConfig.ingredientsSummaryEnabled()
		)));
		builder.addDisplayCategory(category("tooltips", List.of(
			clientConfig.bookmarkTooltipPreviewEnabled(),
			clientConfig.bookmarkTooltipIngredientsEnabled(),
			clientConfig.holdShiftToShowBookmarkTooltipFeaturesEnabled(),
			clientConfig.showCreativeTabNamesEnabled(),
			clientConfig.tagContentTooltipEnabled(),
			clientConfig.hideSingleTagContentTooltipEnabled(),
			clientConfig.ingredientsSummaryEnabled()
		)));
		builder.addDisplayCategory(category("lookups", List.of(
			clientConfig.lookupFluidContentsEnabled(),
			clientConfig.lookupBlockTagsEnabled(),
			clientConfig.lookupHistoryEnabled(),
			clientConfig.maxLookupHistoryRows(),
			clientConfig.maxLookupHistoryIngredients(),
			clientConfig.lookupHistoryDisplaySide()
		)));
		builder.addDisplayCategory(category("cheating", List.of(
			clientConfig.giveMode(),
			clientConfig.cheatToHotbarUsingHotkeysEnabled(),
			clientConfig.showHiddenIngredients()
		)));
		builder.addDisplayCategory(category("advanced", List.of(
			clientConfig.catchRenderErrorsEnabled(),
			clientConfig.lowMemorySlowSearchEnabled()
		)));
	}

	private static ConfigDisplayCategory category(String name, List<? extends IJeiConfigValue<?>> values) {
		return category(name, ConfigDisplayCategoryRole.DEFAULT, values);
	}

	private static ConfigDisplayCategory category(
		String name,
		ConfigDisplayCategoryRole role,
		List<? extends IJeiConfigValue<?>> values
	) {
		return new ConfigDisplayCategory(
			"jei.config.client." + name,
			name,
			role,
			List.copyOf(values)
		);
	}

	public void register(FileWatcher fileWatcher, ConfigManager configManager) {
		schema.register(fileWatcher, configManager);
	}

	public IConfigSchema getSchema() {
		return schema;
	}

	@Override
	public IClientConfig getClientConfig() {
		return clientConfig;
	}

	@Override
	public IIngredientFilterConfig getIngredientFilterConfig() {
		return ingredientFilterConfig;
	}

	@Override
	public IIngredientGridConfig getIngredientListConfig() {
		return ingredientListConfig;
	}

	@Override
	public IIngredientGridConfig getBookmarkListConfig() {
		return bookmarkListConfig;
	}

	@Override
	public void onRuntimeStopped() {
		schema.clearListeners();
	}
}
