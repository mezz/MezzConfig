package mezz.jei.common.config;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.runtime.config.ConfigValueUpdateType;
import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.IConfigCategoryBuilder;
import mezz.jei.common.config.file.IConfigSchemaBuilder;
import mezz.jei.common.util.NavigationVisibility;

public class IngredientGridConfig implements IIngredientGridConfig {
	private static final int minNumRows = 1;
	private static final int defaultNumRows = 16;
	private static final int largestNumRows = 100;

	private static final int minNumColumns = 2;
	private static final int defaultNumColumns = 9;
	private static final int largestNumColumns = 100;

	private static final VerticalAlignment defaultVerticalAlignment = VerticalAlignment.TOP;
	private static final NavigationVisibility defaultButtonNavigationVisibility = NavigationVisibility.ENABLED;
	private static final boolean defaultDrawBackground = false;

	private final ConfigValue<Integer> maxRows;
	private final ConfigValue<Integer> maxColumns;
	private final ConfigValue<HorizontalAlignment> horizontalAlignment;
	private final ConfigValue<VerticalAlignment> verticalAlignment;
	private final ConfigValue<NavigationVisibility> buttonNavigationVisibility;
	private final ConfigValue<Boolean> drawBackground;

	public IngredientGridConfig(String categoryName, IConfigSchemaBuilder builder, HorizontalAlignment defaultHorizontalAlignment) {
		IConfigCategoryBuilder category = builder.addCategory(categoryName);
		maxRows = category.addInteger(
			"maxRows",
			defaultNumRows,
			minNumRows,
			largestNumRows,
			ConfigValueUpdateType.IMMEDIATE
		);
		maxColumns = category.addInteger(
			"maxColumns",
			defaultNumColumns,
			minNumColumns,
			largestNumColumns,
			ConfigValueUpdateType.IMMEDIATE
		);
		horizontalAlignment = category.addEnum("horizontalAlignment", defaultHorizontalAlignment, ConfigValueUpdateType.IMMEDIATE);
		verticalAlignment = category.addEnum("verticalAlignment", defaultVerticalAlignment, ConfigValueUpdateType.IMMEDIATE);
		buttonNavigationVisibility = category.addEnum("buttonNavigationVisibility", defaultButtonNavigationVisibility, ConfigValueUpdateType.IMMEDIATE);
		drawBackground = category.addBoolean("drawBackground", defaultDrawBackground, ConfigValueUpdateType.IMMEDIATE);
	}

	@Override
	public int getMinColumns() {
		return minNumColumns;
	}

	@Override
	public int getMinRows() {
		return minNumRows;
	}

	@Override
	public ConfigValue<Integer> maxColumns() {
		return maxColumns;
	}

	@Override
	public ConfigValue<Integer> maxRows() {
		return maxRows;
	}

	@Override
	public ConfigValue<Boolean> drawBackground() {
		return drawBackground;
	}

	@Override
	public ConfigValue<HorizontalAlignment> horizontalAlignment() {
		return horizontalAlignment;
	}

	@Override
	public ConfigValue<VerticalAlignment> verticalAlignment() {
		return verticalAlignment;
	}

	@Override
	public ConfigValue<NavigationVisibility> buttonNavigationVisibility() {
		return buttonNavigationVisibility;
	}
}
