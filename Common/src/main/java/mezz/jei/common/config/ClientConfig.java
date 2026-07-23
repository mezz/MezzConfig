package mezz.jei.common.config;

import com.google.common.base.Preconditions;
import net.mezzdev.config.value.ConfigValueUpdateType;
import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.schema.IConfigCategoryBuilder;
import net.mezzdev.config.schema.IConfigSchemaBuilder;
import mezz.jei.common.config.serializers.EnumSerializer;
import mezz.jei.common.config.serializers.EnumSerializerWithAliases;
import mezz.jei.common.config.serializers.ListSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class ClientConfig implements IClientConfig {
	@Nullable
	private static IClientConfig instance;

	// appearance
	private final IConfigValue<SearchBarPosition> searchBarPosition;
	private final IConfigValue<Integer> maxRecipeGuiHeight;
	private final IConfigValue<Boolean> toastReflowEnabled;

	// cheat_mode
	private final IConfigValue<GiveMode> giveMode;
	private final IConfigValue<Boolean> cheatToHotbarUsingHotkeysEnabled;
	private final IConfigValue<Boolean> showHiddenIngredients;

	// bookmarks
	private final IConfigValue<BookmarkAddPosition> bookmarkAddPosition;
	private final IConfigValue<Boolean> bookmarkTooltipPreviewEnabled;
	private final IConfigValue<Boolean> bookmarkTooltipIngredientsEnabled;
	private final IConfigValue<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled;
	private final IConfigValue<Boolean> dragToRearrangeBookmarksEnabled;

	// lookup history
	private final IConfigValue<Boolean> lookupHistoryEnabled;
	private final IConfigValue<Integer> maxLookupHistoryRows;
	private final IConfigValue<Integer> maxLookupHistoryIngredients;
	private final IConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide;

	// recipes gui
	private final IConfigValue<Boolean> ingredientsSummaryEnabled;
	private final IConfigValue<Boolean> showTagRecipesEnabled;

	// advanced
	private final IConfigValue<Boolean> lowMemorySlowSearchEnabled;
	private final IConfigValue<Boolean> catchRenderErrorsEnabled;
	private final IConfigValue<Boolean> lookupFluidContentsEnabled;
	private final IConfigValue<Boolean> lookupBlockTagsEnabled;
	private final IConfigValue<Boolean> showCreativeTabNamesEnabled;

	// input
	private final IConfigValue<Integer> dragDelayMs;
	private final IConfigValue<Integer> smoothScrollRate;

	// sorting
	private final IConfigValue<List<IngredientSortStage>> ingredientSorterStages;
	private final IConfigValue<Boolean> recipeSortingBookmarksEnabled;
	private final IConfigValue<Boolean> recipeSortingCraftableEnabled;

	// tags
	private final IConfigValue<Boolean> tagContentTooltipEnabled;
	private final IConfigValue<Boolean> hideSingleTagContentTooltipEnabled;

	public ClientConfig(IConfigSchemaBuilder schema, boolean isDev) {
		instance = this;

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
		showHiddenIngredients = cheating.addBoolean("showHiddenIngredients", false, ConfigValueUpdateType.RESTART);

		IConfigCategoryBuilder recipes = schema.addCategory("recipes");
		showTagRecipesEnabled = recipes.addBoolean("showTagRecipesEnabled", true, ConfigValueUpdateType.RESTART);

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
	public IConfigValue<SearchBarPosition> searchBarPosition() {
		return searchBarPosition;
	}

	@Override
	public IConfigValue<Integer> maxRecipeGuiHeight() {
		return maxRecipeGuiHeight;
	}

	@Override
	public IConfigValue<Boolean> toastReflowEnabled() {
		return toastReflowEnabled;
	}

	@Override
	public IConfigValue<GiveMode> giveMode() {
		return giveMode;
	}

	@Override
	public IConfigValue<Boolean> cheatToHotbarUsingHotkeysEnabled() {
		return cheatToHotbarUsingHotkeysEnabled;
	}

	@Override
	public IConfigValue<Boolean> showHiddenIngredients() {
		return showHiddenIngredients;
	}

	@Override
	public IConfigValue<BookmarkAddPosition> bookmarkAddPosition() {
		return bookmarkAddPosition;
	}

	@Override
	public IConfigValue<Boolean> bookmarkTooltipPreviewEnabled() {
		return bookmarkTooltipPreviewEnabled;
	}

	@Override
	public IConfigValue<Boolean> bookmarkTooltipIngredientsEnabled() {
		return bookmarkTooltipIngredientsEnabled;
	}

	@Override
	public IConfigValue<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled() {
		return holdShiftToShowBookmarkTooltipFeaturesEnabled;
	}

	@Override
	public IConfigValue<Boolean> dragToRearrangeBookmarksEnabled() {
		return dragToRearrangeBookmarksEnabled;
	}

	@Override
	public IConfigValue<Boolean> lookupHistoryEnabled() {
		return lookupHistoryEnabled;
	}

	@Override
	public IConfigValue<Integer> maxLookupHistoryRows() {
		return maxLookupHistoryRows;
	}

	@Override
	public IConfigValue<Integer> maxLookupHistoryIngredients() {
		return maxLookupHistoryIngredients;
	}

	@Override
	public IConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide() {
		return lookupHistoryDisplaySide;
	}

	@Override
	public IConfigValue<Boolean> ingredientsSummaryEnabled() {
		return ingredientsSummaryEnabled;
	}

	@Override
	public IConfigValue<Boolean> showTagRecipesEnabled() {
		return showTagRecipesEnabled;
	}

	@Override
	public IConfigValue<Boolean> lowMemorySlowSearchEnabled() {
		return lowMemorySlowSearchEnabled;
	}

	@Override
	public IConfigValue<Boolean> catchRenderErrorsEnabled() {
		return catchRenderErrorsEnabled;
	}

	@Override
	public IConfigValue<Boolean> lookupFluidContentsEnabled() {
		return lookupFluidContentsEnabled;
	}

	@Override
	public IConfigValue<Boolean> lookupBlockTagsEnabled() {
		return lookupBlockTagsEnabled;
	}

	@Override
	public IConfigValue<Boolean> showCreativeTabNamesEnabled() {
		return showCreativeTabNamesEnabled;
	}

	@Override
	public IConfigValue<Integer> dragDelayMs() {
		return dragDelayMs;
	}

	@Override
	public IConfigValue<Integer> smoothScrollRate() {
		return smoothScrollRate;
	}

	@Override
	public IConfigValue<List<IngredientSortStage>> ingredientSorterStages() {
		return ingredientSorterStages;
	}

	@Override
	public IConfigValue<Boolean> recipeSortingBookmarksEnabled() {
		return recipeSortingBookmarksEnabled;
	}

	@Override
	public IConfigValue<Boolean> recipeSortingCraftableEnabled() {
		return recipeSortingCraftableEnabled;
	}

	@Override
	public IConfigValue<Boolean> tagContentTooltipEnabled() {
		return tagContentTooltipEnabled;
	}

	@Override
	public IConfigValue<Boolean> hideSingleTagContentTooltipEnabled() {
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
