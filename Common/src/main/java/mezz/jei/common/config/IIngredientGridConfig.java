package mezz.jei.common.config;

import net.mezzdev.config.IConfigValue;
import net.mezzdev.config.alignment.HorizontalConfigAlignment;
import net.mezzdev.config.alignment.VerticalConfigAlignment;

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
