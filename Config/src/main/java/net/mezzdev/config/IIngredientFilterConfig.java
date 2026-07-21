package net.mezzdev.config;


public interface IIngredientFilterConfig {
	IJeiConfigValue<SearchMode> modNameSearchMode();

	IJeiConfigValue<SearchMode> tooltipSearchMode();

	IJeiConfigValue<SearchMode> tagSearchMode();

	IJeiConfigValue<SearchMode> colorSearchMode();

	IJeiConfigValue<SearchMode> resourceLocationSearchMode();

	IJeiConfigValue<SearchMode> creativeTabSearchMode();

	IJeiConfigValue<Boolean> searchAdvancedTooltips();

	IJeiConfigValue<Boolean> searchModIds();

	IJeiConfigValue<Boolean> searchModAliases();

	IJeiConfigValue<Boolean> searchIngredientAliases();

	IJeiConfigValue<Boolean> searchShortModNames();
}
