package mezz.jei.common.config;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import net.mezzdev.config.api.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.api.schema.IConfigEditableSchema;
import net.mezzdev.config.api.schema.IConfigSchemaBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.minecraft.client.KeyMapping;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ClientConfigs implements IClientConfigs {
	private final IClientConfig clientConfig;
	private final IIngredientFilterConfig ingredientFilterConfig;
	private final IIngredientGridConfig ingredientListConfig;
	private final IIngredientGridConfig bookmarkListConfig;

	private final IConfigEditableSchema schema;

	public ClientConfigs(
		IConfigSchemaBuilder builder,
		String localizationPath,
		boolean isDev,
		Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier
	) {
		ClientConfig clientConfig = new ClientConfig(builder, isDev);
		IngredientFilterConfig ingredientFilterConfig = new IngredientFilterConfig(builder);
		IngredientGridConfig ingredientListConfig = new IngredientGridConfig(localizationPath, "ingredientList", builder, HorizontalAlignment.RIGHT);
		IngredientGridConfig bookmarkListConfig = new IngredientGridConfig(localizationPath, "bookmarkList", builder, HorizontalAlignment.LEFT);
		addDisplayCategories(builder, clientConfig, ingredientFilterConfig, ingredientListConfig, bookmarkListConfig, keyMappingsSupplier);

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
		IngredientGridConfig bookmarkListConfig,
		Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier
	) {
		addDisplayCategory(builder, "search", List.of(
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
		));
		addDisplayCategory(builder, "ingredientList", List.of(
			ingredientListConfig.maxRows(),
			ingredientListConfig.maxColumns(),
			ingredientListConfig.alignment(),
			ingredientListConfig.buttonNavigationVisibility(),
			ingredientListConfig.drawBackground(),
			clientConfig.ingredientSorterStages(),
			clientConfig.toastReflowEnabled()
		));
		addDisplayCategory(builder, "bookmarkList", List.of(
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
		));
		addDisplayCategory(builder, "input", List.of(
			clientConfig.dragDelayMs(),
			clientConfig.smoothScrollRate()
		))
			.addKeyMappings(keyMappingsSupplier);
		addDisplayCategory(builder, "recipes", List.of(
			clientConfig.showTagRecipesEnabled(),
			clientConfig.maxRecipeGuiHeight(),
			clientConfig.recipeSortingBookmarksEnabled(),
			clientConfig.recipeSortingCraftableEnabled(),
			clientConfig.ingredientsSummaryEnabled()
		));
		addDisplayCategory(builder, "tooltips", List.of(
			clientConfig.bookmarkTooltipPreviewEnabled(),
			clientConfig.bookmarkTooltipIngredientsEnabled(),
			clientConfig.holdShiftToShowBookmarkTooltipFeaturesEnabled(),
			clientConfig.showCreativeTabNamesEnabled(),
			clientConfig.tagContentTooltipEnabled(),
			clientConfig.hideSingleTagContentTooltipEnabled(),
			clientConfig.ingredientsSummaryEnabled()
		));
		addDisplayCategory(builder, "lookups", List.of(
			clientConfig.lookupFluidContentsEnabled(),
			clientConfig.lookupBlockTagsEnabled(),
			clientConfig.lookupHistoryEnabled(),
			clientConfig.maxLookupHistoryRows(),
			clientConfig.maxLookupHistoryIngredients(),
			clientConfig.lookupHistoryDisplaySide()
		));
		addDisplayCategory(builder, "cheating", List.of(
			clientConfig.giveMode(),
			clientConfig.cheatToHotbarUsingHotkeysEnabled(),
			clientConfig.showHiddenIngredients()
		));
		addDisplayCategory(builder, "advanced", List.of(
			clientConfig.catchRenderErrorsEnabled(),
			clientConfig.lowMemorySlowSearchEnabled()
		));
	}

	private static IConfigDisplayCategoryBuilder addDisplayCategory(
		IConfigSchemaBuilder builder,
		String name,
		List<? extends IConfigValue<?>> values
	) {
		return builder.addDisplayCategory(name)
			.addValues(values);
	}

	public IConfigEditableSchema getSchema() {
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
