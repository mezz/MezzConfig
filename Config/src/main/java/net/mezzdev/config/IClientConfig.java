package net.mezzdev.config;


import java.util.List;

public interface IClientConfig {
	int minRecipeGuiHeight = 175;
	int defaultRecipeGuiHeight = 350;
	boolean defaultCenterSearchBar = false;

	IJeiConfigValue<SearchBarPosition> searchBarPosition();

	IJeiConfigValue<Integer> maxRecipeGuiHeight();

	IJeiConfigValue<Boolean> toastReflowEnabled();

	IJeiConfigValue<GiveMode> giveMode();

	IJeiConfigValue<Boolean> cheatToHotbarUsingHotkeysEnabled();

	IJeiConfigValue<Boolean> showHiddenIngredients();

	IJeiConfigValue<BookmarkAddPosition> bookmarkAddPosition();

	IJeiConfigValue<Boolean> bookmarkTooltipPreviewEnabled();

	IJeiConfigValue<Boolean> bookmarkTooltipIngredientsEnabled();

	IJeiConfigValue<Boolean> holdShiftToShowBookmarkTooltipFeaturesEnabled();

	IJeiConfigValue<Boolean> dragToRearrangeBookmarksEnabled();

	IJeiConfigValue<Boolean> lookupHistoryEnabled();

	IJeiConfigValue<Integer> maxLookupHistoryRows();

	IJeiConfigValue<Integer> maxLookupHistoryIngredients();

	IJeiConfigValue<HistoryDisplaySide> lookupHistoryDisplaySide();

	IJeiConfigValue<Boolean> ingredientsSummaryEnabled();

	IJeiConfigValue<Boolean> showTagRecipesEnabled();

	IJeiConfigValue<Boolean> lowMemorySlowSearchEnabled();

	IJeiConfigValue<Boolean> catchRenderErrorsEnabled();

	IJeiConfigValue<Boolean> lookupFluidContentsEnabled();

	IJeiConfigValue<Boolean> lookupBlockTagsEnabled();

	IJeiConfigValue<Boolean> showCreativeTabNamesEnabled();

	IJeiConfigValue<Integer> dragDelayMs();

	IJeiConfigValue<Integer> smoothScrollRate();

	IJeiConfigValue<List<IngredientSortStage>> ingredientSorterStages();

	IJeiConfigValue<Boolean> recipeSortingBookmarksEnabled();

	IJeiConfigValue<Boolean> recipeSortingCraftableEnabled();

	IJeiConfigValue<Boolean> tagContentTooltipEnabled();

	IJeiConfigValue<Boolean> hideSingleTagContentTooltipEnabled();
}
