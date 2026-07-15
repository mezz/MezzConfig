package mezz.jei.common.config;

import com.google.common.base.Preconditions;
import mezz.jei.api.runtime.config.ConfigValueUpdateType;
import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.IConfigCategoryBuilder;
import mezz.jei.common.config.file.IConfigSchemaBuilder;
import mezz.jei.common.config.file.serializers.EnumSerializer;
import mezz.jei.common.config.file.serializers.EnumSerializerWithAliases;
import mezz.jei.common.config.file.serializers.ListSerializer;
import mezz.jei.common.platform.Services;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class ClientConfig implements IClientConfig {
	@Nullable
	private static IClientConfig instance;

	// appearance
	private final ConfigValue<SearchBarPosition> searchBarPosition;
	private final ConfigValue<Integer> maxRecipeGuiHeight;
	private final ConfigValue<Boolean> toastReflowEnabled;

	// cheat_mode
	private final ConfigValue<GiveMode> giveMode;
	private final ConfigValue<Boolean> cheatToHotbarUsingHotkeysEnabled;
	private final ConfigValue<Boolean> showHiddenIngredients;

	// bookmarks
	private final ConfigValue<BookmarkAddPosition> bookmarkAddPosition;
	private final ConfigValue<Boolean> bookmarkTooltipPreviewEnabled;
	private final ConfigValue<Boolean> bookmarkTooltipIngredientsEnabled;
	private final ConfigValue<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled;
	private final ConfigValue<Boolean> dragToRearrangeBookmarksEnabled;

	// lookup history
	private final ConfigValue<Boolean> lookupHistoryEnabled;
	private final ConfigValue<Integer> maxLookupHistoryRows;
	private final ConfigValue<Integer> maxLookupHistoryIngredients;
	private final ConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide;

	// recipes gui
	private final ConfigValue<Boolean> ingredientsSummaryEnabled;
	private final ConfigValue<Boolean> showTagRecipesEnabled;

	// advanced
	private final ConfigValue<Boolean> lowMemorySlowSearchEnabled;
	private final ConfigValue<Boolean> catchRenderErrorsEnabled;
	private final ConfigValue<Boolean> lookupFluidContentsEnabled;
	private final ConfigValue<Boolean> lookupBlockTagsEnabled;
	private final ConfigValue<Boolean> showCreativeTabNamesEnabled;

	// input
	private final ConfigValue<Integer> dragDelayMs;
	private final ConfigValue<Integer> smoothScrollRate;

	// sorting
	private final ConfigValue<List<IngredientSortStage>> ingredientSorterStages;
	private final ConfigValue<Boolean> recipeSortingBookmarksEnabled;
	private final ConfigValue<Boolean> recipeSortingCraftableEnabled;

	// tags
	private final ConfigValue<Boolean> tagContentTooltipEnabled;
	private final ConfigValue<Boolean> hideSingleTagContentTooltipEnabled;

	public ClientConfig(IConfigSchemaBuilder schema) {
		instance = this;

		boolean isDev = Services.PLATFORM.getModHelper().isInDev();

		IConfigCategoryBuilder appearance = schema.addCategory("appearance");
		searchBarPosition = appearance.addValue(
			"centerSearch",
			SearchBarPosition.fromCentered(defaultCenterSearchBar),
			enumWithLegacyBooleanAliases(SearchBarPosition.class, SearchBarPosition.STANDARD, SearchBarPosition.CENTERED),
			ConfigValueUpdateType.IMMEDIATE
		);
		maxRecipeGuiHeight = appearance.addInteger(
			"recipeGuiHeight",
			defaultRecipeGuiHeight,
			minRecipeGuiHeight,
			Integer.MAX_VALUE,
			ConfigValueUpdateType.IMMEDIATE
		);
		toastReflowEnabled = appearance.addBoolean("toastReflowEnabled", true, ConfigValueUpdateType.IMMEDIATE);

		IConfigCategoryBuilder cheating = schema.addCategory("cheating");
		giveMode = cheating.addEnum("giveMode", GiveMode.defaultGiveMode, ConfigValueUpdateType.IMMEDIATE);
		cheatToHotbarUsingHotkeysEnabled = cheating.addBoolean("cheatToHotbarUsingHotkeysEnabled", false, ConfigValueUpdateType.IMMEDIATE);
		showHiddenIngredients = cheating.addBoolean("showHiddenIngredients", false, ConfigValueUpdateType.RESTART_JEI);

		IConfigCategoryBuilder recipes = schema.addCategory("recipes");
		showTagRecipesEnabled = recipes.addBoolean("showTagRecipesEnabled", true, ConfigValueUpdateType.RESTART_JEI);

		IConfigCategoryBuilder bookmarks = schema.addCategory("bookmarks");
		bookmarkAddPosition = bookmarks.addValue(
			"addBookmarksToFrontEnabled",
			BookmarkAddPosition.END,
			enumWithLegacyBooleanAliases(BookmarkAddPosition.class, BookmarkAddPosition.END, BookmarkAddPosition.FRONT),
			ConfigValueUpdateType.IMMEDIATE
		);
		dragToRearrangeBookmarksEnabled = bookmarks.addBoolean("dragToRearrangeBookmarksEnabled", true, ConfigValueUpdateType.IMMEDIATE);

		IConfigCategoryBuilder tooltips = schema.addCategory("tooltips");
		bookmarkTooltipPreviewEnabled = tooltips.addBoolean("bookmarkTooltipPreview", true, ConfigValueUpdateType.IMMEDIATE);
		bookmarkTooltipIngredientsEnabled = tooltips.addBoolean("bookmarkTooltipIngredients", false, ConfigValueUpdateType.IMMEDIATE);
		holdShiftToShowBookmarkTooltipFeaturesEnabled = tooltips.addBoolean("holdShiftToShowBookmarkTooltipFeatures", true, ConfigValueUpdateType.IMMEDIATE);
		showCreativeTabNamesEnabled = tooltips.addBoolean("showCreativeTabNamesEnabled", false, ConfigValueUpdateType.IMMEDIATE);
		tagContentTooltipEnabled = tooltips.addBoolean("tagContentTooltipEnabled", true, ConfigValueUpdateType.IMMEDIATE);
		hideSingleTagContentTooltipEnabled = tooltips.addBoolean("hideSingleTagContentTooltipEnabled", true, ConfigValueUpdateType.IMMEDIATE);
		ingredientsSummaryEnabled = tooltips.addBoolean("enableRecipesGuiIngredientsSummary", false, ConfigValueUpdateType.IMMEDIATE);

		IConfigCategoryBuilder performance = schema.addCategory("performance");
		lowMemorySlowSearchEnabled = performance.addBoolean("lowMemorySlowSearchEnabled", false, ConfigValueUpdateType.ON_APPLY);

		IConfigCategoryBuilder lookups = schema.addCategory("lookups");
		lookupFluidContentsEnabled = lookups.addBoolean("lookupFluidContentsEnabled", false, ConfigValueUpdateType.IMMEDIATE);
		lookupBlockTagsEnabled = lookups.addBoolean("lookupBlockTagsEnabled", true, ConfigValueUpdateType.IMMEDIATE);

		IConfigCategoryBuilder lookupHistory = schema.addCategory("lookupHistory");

		lookupHistoryEnabled = lookupHistory.addBoolean(
			"enabled",
			false,
			ConfigValueUpdateType.IMMEDIATE
		);
		maxLookupHistoryRows = lookupHistory.addInteger(
			"maxRows",
			2,
			1,
			7,
			ConfigValueUpdateType.IMMEDIATE
		);
		maxLookupHistoryIngredients = lookupHistory.addInteger(
			"maxIngredients",
			100,
			10,
			1_000,
			ConfigValueUpdateType.IMMEDIATE
		);
		lookupHistoryDisplaySide = lookupHistory.addEnum(
			"displaySide",
			HistoryDisplaySide.LEFT,
			ConfigValueUpdateType.IMMEDIATE
		);

		IConfigCategoryBuilder advanced = schema.addCategory("advanced");
		catchRenderErrorsEnabled = advanced.addBoolean("catchRenderErrorsEnabled", !isDev, ConfigValueUpdateType.IMMEDIATE);

		IConfigCategoryBuilder input = schema.addCategory("input");
		dragDelayMs = input.addInteger(
			"dragDelayInMilliseconds",
			150,
			0,
			1000,
			ConfigValueUpdateType.IMMEDIATE
		);
		smoothScrollRate = input.addInteger(
			"smoothScrollRate",
			9,
			1,
			50,
			ConfigValueUpdateType.IMMEDIATE
		);

		IConfigCategoryBuilder sorting = schema.addCategory("sorting");
		ingredientSorterStages = sorting.addList(
			"ingredientSortStages",
			IngredientSortStage.defaultStages,
			new ListSerializer<>(new EnumSerializer<>(IngredientSortStage.class)),
			ConfigValueUpdateType.ON_APPLY
		);
		recipeSortingBookmarksEnabled = sorting.addBoolean("recipeSortingBookmarks", true, ConfigValueUpdateType.IMMEDIATE);
		recipeSortingCraftableEnabled = sorting.addBoolean("recipeSortingCraftable", true, ConfigValueUpdateType.IMMEDIATE);
	}

	/**
	 * Only use this for hacky stuff like the debug plugin
	 */
	@Deprecated
	public static IClientConfig getInstance() {
		Preconditions.checkNotNull(instance);
		return instance;
	}

	@Override
	public ConfigValue<SearchBarPosition> searchBarPosition() {
		return searchBarPosition;
	}

	@Override
	public ConfigValue<Integer> maxRecipeGuiHeight() {
		return maxRecipeGuiHeight;
	}

	@Override
	public ConfigValue<Boolean> toastReflowEnabled() {
		return toastReflowEnabled;
	}

	@Override
	public ConfigValue<GiveMode> giveMode() {
		return giveMode;
	}

	@Override
	public ConfigValue<Boolean> cheatToHotbarUsingHotkeysEnabled() {
		return cheatToHotbarUsingHotkeysEnabled;
	}

	@Override
	public ConfigValue<Boolean> showHiddenIngredients() {
		return showHiddenIngredients;
	}

	@Override
	public ConfigValue<BookmarkAddPosition> bookmarkAddPosition() {
		return bookmarkAddPosition;
	}

	@Override
	public ConfigValue<Boolean> bookmarkTooltipPreviewEnabled() {
		return bookmarkTooltipPreviewEnabled;
	}

	@Override
	public ConfigValue<Boolean> bookmarkTooltipIngredientsEnabled() {
		return bookmarkTooltipIngredientsEnabled;
	}

	@Override
	public ConfigValue<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled() {
		return holdShiftToShowBookmarkTooltipFeaturesEnabled;
	}

	@Override
	public ConfigValue<Boolean> dragToRearrangeBookmarksEnabled() {
		return dragToRearrangeBookmarksEnabled;
	}

	@Override
	public ConfigValue<Boolean> lookupHistoryEnabled() {
		return lookupHistoryEnabled;
	}

	@Override
	public ConfigValue<Integer> maxLookupHistoryRows() {
		return maxLookupHistoryRows;
	}

	@Override
	public ConfigValue<Integer> maxLookupHistoryIngredients() {
		return maxLookupHistoryIngredients;
	}

	@Override
	public ConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide() {
		return lookupHistoryDisplaySide;
	}

	@Override
	public ConfigValue<Boolean> ingredientsSummaryEnabled() {
		return ingredientsSummaryEnabled;
	}

	@Override
	public ConfigValue<Boolean> showTagRecipesEnabled() {
		return showTagRecipesEnabled;
	}

	@Override
	public ConfigValue<Boolean> lowMemorySlowSearchEnabled() {
		return lowMemorySlowSearchEnabled;
	}

	@Override
	public ConfigValue<Boolean> catchRenderErrorsEnabled() {
		return catchRenderErrorsEnabled;
	}

	@Override
	public ConfigValue<Boolean> lookupFluidContentsEnabled() {
		return lookupFluidContentsEnabled;
	}

	@Override
	public ConfigValue<Boolean> lookupBlockTagsEnabled() {
		return lookupBlockTagsEnabled;
	}

	@Override
	public ConfigValue<Boolean> showCreativeTabNamesEnabled() {
		return showCreativeTabNamesEnabled;
	}

	@Override
	public ConfigValue<Integer> dragDelayMs() {
		return dragDelayMs;
	}

	@Override
	public ConfigValue<Integer> smoothScrollRate() {
		return smoothScrollRate;
	}

	@Override
	public ConfigValue<List<IngredientSortStage>> ingredientSorterStages() {
		return ingredientSorterStages;
	}

	@Override
	public ConfigValue<Boolean> recipeSortingBookmarksEnabled() {
		return recipeSortingBookmarksEnabled;
	}

	@Override
	public ConfigValue<Boolean> recipeSortingCraftableEnabled() {
		return recipeSortingCraftableEnabled;
	}

	@Override
	public ConfigValue<Boolean> tagContentTooltipEnabled() {
		return tagContentTooltipEnabled;
	}

	@Override
	public ConfigValue<Boolean> hideSingleTagContentTooltipEnabled() {
		return hideSingleTagContentTooltipEnabled;
	}

	private static <T extends Enum<T>> EnumSerializerWithAliases<T> enumWithLegacyBooleanAliases(
		Class<T> enumClass,
		T falseValue,
		T trueValue
	) {
		return new EnumSerializerWithAliases<>(
			enumClass,
			Map.of(
				"false", falseValue,
				"true", trueValue
			)
		);
	}
}
