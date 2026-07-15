package mezz.jei.common.config;

import com.google.common.base.Preconditions;
import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.IConfigCategoryBuilder;
import mezz.jei.common.config.file.IConfigListener;
import mezz.jei.common.config.file.IConfigSchemaBuilder;
import mezz.jei.common.config.file.serializers.EnumSerializer;
import mezz.jei.common.config.file.serializers.EnumSerializerWithAliases;
import mezz.jei.common.config.file.serializers.ListSerializer;
import mezz.jei.common.platform.Services;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class ClientConfig implements IClientConfig {
	@Nullable
	private static IClientConfig instance;

	// appearance
	private final Supplier<SearchBarPosition> searchBarPosition;
	private final Supplier<Integer> maxRecipeGuiHeight;
	private final Supplier<Boolean> toastReflowEnabled;

	// cheat_mode
	private final Supplier<GiveMode> giveMode;
	private final Supplier<Boolean> cheatToHotbarUsingHotkeysEnabled;
	private final Supplier<Boolean> showHiddenIngredients;

	// bookmarks
	private final Supplier<BookmarkAddPosition> bookmarkAddPosition;
	private final Supplier<Boolean> bookmarkTooltipPreviewEnabled;
	private final Supplier<Boolean> bookmarkTooltipIngredientsEnabled;
	private final Supplier<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled;
	private final Supplier<Boolean> dragToRearrangeBookmarksEnabled;

	// lookup history
	private final ConfigValue<Boolean> lookupHistoryEnabled;
	private final ConfigValue<Integer> maxLookupHistoryRows;
	private final ConfigValue<Integer> maxLookupHistoryIngredients;
	private final ConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide;

	// recipes gui
	private final ConfigValue<Boolean> ingredientsSummaryEnabled;
	private final Supplier<Boolean> showTagRecipesEnabled;

	// advanced
	private final Supplier<Boolean> lowMemorySlowSearchEnabled;
	private final Supplier<Boolean> catchRenderErrorsEnabled;
	private final Supplier<Boolean> lookupFluidContentsEnabled;
	private final Supplier<Boolean> lookupBlockTagsEnabled;
	private final Supplier<Boolean> showCreativeTabNamesEnabled;

	// input
	private final Supplier<Integer> dragDelayMs;
	private final Supplier<Integer> smoothScrollRate;

	// sorting
	private final Supplier<List<IngredientSortStage>> ingredientSorterStages;
	private final ConfigValue<Boolean> recipeSortingBookmarksEnabled;
	private final ConfigValue<Boolean> recipeSortingCraftableEnabled;

	// tags
	private final Supplier<Boolean> tagContentTooltipEnabled;
	private final Supplier<Boolean> hideSingleTagContentTooltipEnabled;

	public ClientConfig(IConfigSchemaBuilder schema) {
		instance = this;

		boolean isDev = Services.PLATFORM.getModHelper().isInDev();

		IConfigCategoryBuilder appearance = schema.addCategory("appearance");
		searchBarPosition = appearance.addValue(
			"centerSearch",
			SearchBarPosition.fromCentered(defaultCenterSearchBar),
			enumWithLegacyBooleanAliases(SearchBarPosition.class, SearchBarPosition.STANDARD, SearchBarPosition.CENTERED)
		);
		maxRecipeGuiHeight = appearance.addInteger(
			"recipeGuiHeight",
			defaultRecipeGuiHeight,
			minRecipeGuiHeight,
			Integer.MAX_VALUE
		);
		toastReflowEnabled = appearance.addBoolean("toastReflowEnabled", true);

		IConfigCategoryBuilder cheating = schema.addCategory("cheating");
		giveMode = cheating.addEnum("giveMode", GiveMode.defaultGiveMode);
		cheatToHotbarUsingHotkeysEnabled = cheating.addBoolean("cheatToHotbarUsingHotkeysEnabled", false);
		showHiddenIngredients = cheating.addBoolean("showHiddenIngredients", false);

		IConfigCategoryBuilder recipes = schema.addCategory("recipes");
		showTagRecipesEnabled = recipes.addBoolean("showTagRecipesEnabled", true);

		IConfigCategoryBuilder bookmarks = schema.addCategory("bookmarks");
		bookmarkAddPosition = bookmarks.addValue(
			"addBookmarksToFrontEnabled",
			BookmarkAddPosition.END,
			enumWithLegacyBooleanAliases(BookmarkAddPosition.class, BookmarkAddPosition.END, BookmarkAddPosition.FRONT)
		);
		dragToRearrangeBookmarksEnabled = bookmarks.addBoolean("dragToRearrangeBookmarksEnabled", true);

		IConfigCategoryBuilder tooltips = schema.addCategory("tooltips");
		bookmarkTooltipPreviewEnabled = tooltips.addBoolean("bookmarkTooltipPreview", true);
		bookmarkTooltipIngredientsEnabled = tooltips.addBoolean("bookmarkTooltipIngredients", false);
		holdShiftToShowBookmarkTooltipFeaturesEnabled = tooltips.addBoolean("holdShiftToShowBookmarkTooltipFeatures", true);
		showCreativeTabNamesEnabled = tooltips.addBoolean("showCreativeTabNamesEnabled", false);
		tagContentTooltipEnabled = tooltips.addBoolean("tagContentTooltipEnabled", true);
		hideSingleTagContentTooltipEnabled = tooltips.addBoolean("hideSingleTagContentTooltipEnabled", true);
		ingredientsSummaryEnabled = tooltips.addBoolean("enableRecipesGuiIngredientsSummary", false);

		IConfigCategoryBuilder performance = schema.addCategory("performance");
		lowMemorySlowSearchEnabled = performance.addBoolean("lowMemorySlowSearchEnabled", false);

		IConfigCategoryBuilder lookups = schema.addCategory("lookups");
		lookupFluidContentsEnabled = lookups.addBoolean("lookupFluidContentsEnabled", false);
		lookupBlockTagsEnabled = lookups.addBoolean("lookupBlockTagsEnabled", true);

		IConfigCategoryBuilder lookupHistory = schema.addCategory("lookupHistory");

		lookupHistoryEnabled = lookupHistory.addBoolean(
			"enabled",
			false
		);
		maxLookupHistoryRows = lookupHistory.addInteger(
			"maxRows",
			2,
			1,
			7
		);
		maxLookupHistoryIngredients = lookupHistory.addInteger(
			"maxIngredients",
			100,
			10,
			1_000
		);
		lookupHistoryDisplaySide = lookupHistory.addEnum(
			"displaySide",
			HistoryDisplaySide.LEFT
		);

		IConfigCategoryBuilder advanced = schema.addCategory("advanced");
		catchRenderErrorsEnabled = advanced.addBoolean("catchRenderErrorsEnabled", !isDev);

		IConfigCategoryBuilder input = schema.addCategory("input");
		dragDelayMs = input.addInteger(
			"dragDelayInMilliseconds",
			150,
			0,
			1000
		);
		smoothScrollRate = input.addInteger(
			"smoothScrollRate",
			9,
			1,
			50
		);

		IConfigCategoryBuilder sorting = schema.addCategory("sorting");
		ingredientSorterStages = sorting.addList(
			"ingredientSortStages",
			IngredientSortStage.defaultStages,
			new ListSerializer<>(new EnumSerializer<>(IngredientSortStage.class))
		);
		recipeSortingBookmarksEnabled = sorting.addBoolean("recipeSortingBookmarks", true);
		recipeSortingCraftableEnabled = sorting.addBoolean("recipeSortingCraftable", true);
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
	public boolean isCenterSearchBarEnabled() {
		return searchBarPosition.get().isCentered();
	}

	@Override
	public boolean isLowMemorySlowSearchEnabled() {
		return lowMemorySlowSearchEnabled.get();
	}

	@Override
	public boolean isCatchRenderErrorsEnabled() {
		return catchRenderErrorsEnabled.get();
	}

	@Override
	public boolean isCheatToHotbarUsingHotkeysEnabled() {
		return cheatToHotbarUsingHotkeysEnabled.get();
	}

	@Override
	public boolean isAddingBookmarksToFrontEnabled() {
		return bookmarkAddPosition.get().isFront();
	}

	@Override
	public boolean isLookupFluidContentsEnabled() {
		return lookupFluidContentsEnabled.get();
	}

	@Override
	public boolean isLookupBlockTagsEnabled() {
		return lookupBlockTagsEnabled.get();
	}

	@Override
	public GiveMode getGiveMode() {
		return giveMode.get();
	}

	@Override
	public boolean getShowHiddenIngredients() {
		return showHiddenIngredients.get();
	}

	@Override
	public List<BookmarkTooltipFeature> getBookmarkTooltipFeatures() {
		List<BookmarkTooltipFeature> features = new ArrayList<>(2);
		if (bookmarkTooltipPreviewEnabled.get()) {
			features.add(BookmarkTooltipFeature.PREVIEW);
		}
		if (bookmarkTooltipIngredientsEnabled.get()) {
			features.add(BookmarkTooltipFeature.INGREDIENTS);
		}
		return List.copyOf(features);
	}

	@Override
	public boolean isHoldShiftToShowBookmarkTooltipFeaturesEnabled() {
		return holdShiftToShowBookmarkTooltipFeaturesEnabled.get();
	}

	@Override
	public boolean isDragToRearrangeBookmarksEnabled() {
		return dragToRearrangeBookmarksEnabled.get();
	}

	@Override
	public boolean isLookupHistoryEnabled() {
		return lookupHistoryEnabled.get();
	}

	@Override
	public void setLookupHistoryEnabled(boolean enabled) {
		lookupHistoryEnabled.set(enabled);
	}

	@Override
	public void addLookupHistoryEnabledListener(IConfigListener<Boolean> listener) {
		lookupHistoryEnabled.addListener(listener);
	}

	@Override
	public int getMaxLookupHistoryRows() {
		return maxLookupHistoryRows.get();
	}

    @Override
    public void addMaxLookupHistoryRowsListener(IConfigListener<Integer> listener) {
        maxLookupHistoryRows.addListener(listener);
    }

	@Override
	public int getMaxLookupHistoryIngredients() {
		return maxLookupHistoryIngredients.get();
	}

	@Override
	public HistoryDisplaySide getLookupHistoryDisplaySide() {
		return lookupHistoryDisplaySide.get();
	}

	@Override
	public void addLookupHistoryDisplaySideListener(IConfigListener<HistoryDisplaySide> listener) {
		lookupHistoryDisplaySide.addListener(listener);
	}

	@Override
	public boolean isIngredientsSummaryEnabled() {
		return ingredientsSummaryEnabled.get();
	}

	@Override
	public int getDragDelayMs() {
		return dragDelayMs.get();
	}

	@Override
	public int getSmoothScrollRate() {
		return smoothScrollRate.get();
	}

	@Override
	public int getMaxRecipeGuiHeight() {
		return maxRecipeGuiHeight.get();
	}

	@Override
	public List<IngredientSortStage> getIngredientSorterStages() {
		return ingredientSorterStages.get();
	}

	@Override
	public Set<RecipeSorterStage> getRecipeSorterStages() {
		Set<RecipeSorterStage> stages = EnumSet.noneOf(RecipeSorterStage.class);
		if (recipeSortingBookmarksEnabled.get()) {
			stages.add(RecipeSorterStage.BOOKMARKED);
		}
		if (recipeSortingCraftableEnabled.get()) {
			stages.add(RecipeSorterStage.CRAFTABLE);
		}
		return Set.copyOf(stages);
	}

	@Override
	public void enableRecipeSorterStage(RecipeSorterStage stage) {
		setRecipeSorterStage(stage, true);
	}

	@Override
	public void disableRecipeSorterStage(RecipeSorterStage stage) {
		setRecipeSorterStage(stage, false);
	}

	private void setRecipeSorterStage(RecipeSorterStage stage, boolean enabled) {
		switch (stage) {
			case BOOKMARKED -> recipeSortingBookmarksEnabled.set(enabled);
			case CRAFTABLE -> recipeSortingCraftableEnabled.set(enabled);
		}
	}

	@Override
	public boolean isTagContentTooltipEnabled() {
		return tagContentTooltipEnabled.get();
	}

	@Override
	public boolean getHideSingleTagContentTooltipEnabled() {
		return hideSingleTagContentTooltipEnabled.get();
	}

	@Override
	public boolean isShowTagRecipesEnabled() {
		return showTagRecipesEnabled.get();
	}

	@Override
	public boolean isShowCreativeTabNamesEnabled() {
		return showCreativeTabNamesEnabled.get();
	}

	@Override
	public boolean isToastReflowEnabled() {
		return toastReflowEnabled.get();
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
