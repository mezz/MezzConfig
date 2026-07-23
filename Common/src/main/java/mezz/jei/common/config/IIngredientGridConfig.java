package mezz.jei.common.config;

import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.alignment.HorizontalAlignment;
import net.mezzdev.config.alignment.VerticalAlignment;

public interface IIngredientGridConfig {
	IConfigValue<Integer> maxColumns();

	int getMinColumns();

	IConfigValue<Integer> maxRows();

	int getMinRows();

	IConfigValue<Boolean> drawBackground();

	IConfigValue<HorizontalAlignment> horizontalAlignment();

	IConfigValue<VerticalAlignment> verticalAlignment();

	IConfigValue<NavigationVisibility> buttonNavigationVisibility();
}
