package net.mezzdev.config.client;

import net.mezzdev.config.IConfigValue;

public interface IIngredientGridConfig {
	IConfigValue<Integer> maxColumns();

	int getMinColumns();

	IConfigValue<Integer> maxRows();

	int getMinRows();

	IConfigValue<Boolean> drawBackground();

	IConfigValue<HorizontalConfigAlignment> horizontalAlignment();

	IConfigValue<VerticalConfigAlignment> verticalAlignment();

	IConfigValue<NavigationVisibility> buttonNavigationVisibility();
}
