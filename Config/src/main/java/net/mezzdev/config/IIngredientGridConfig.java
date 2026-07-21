package net.mezzdev.config;


public interface IIngredientGridConfig {
	IJeiConfigValue<Integer> maxColumns();

	int getMinColumns();

	IJeiConfigValue<Integer> maxRows();

	int getMinRows();

	IJeiConfigValue<Boolean> drawBackground();

	IJeiConfigValue<HorizontalConfigAlignment> horizontalAlignment();

	IJeiConfigValue<VerticalConfigAlignment> verticalAlignment();

	IJeiConfigValue<NavigationVisibility> buttonNavigationVisibility();
}
