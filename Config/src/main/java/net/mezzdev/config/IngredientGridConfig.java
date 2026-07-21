package net.mezzdev.config;

import net.mezzdev.config.file.ConfigValue;
import net.mezzdev.config.file.IConfigCategoryBuilder;
import net.mezzdev.config.file.IConfigSchemaBuilder;

public class IngredientGridConfig implements IIngredientGridConfig {
	private static final int minNumRows = 1;
	private static final int defaultNumRows = 16;
	private static final int largestNumRows = 100;

	private static final int minNumColumns = 2;
	private static final int defaultNumColumns = 9;
	private static final int largestNumColumns = 100;

	private static final VerticalConfigAlignment defaultVerticalAlignment = VerticalConfigAlignment.TOP;
	private static final NavigationVisibility defaultButtonNavigationVisibility = NavigationVisibility.ENABLED;
	private static final boolean defaultDrawBackground = false;

	private final ConfigValue<Integer> maxRows;
	private final ConfigValue<Integer> maxColumns;
	private final ConfigValue<HorizontalConfigAlignment> horizontalAlignment;
	private final ConfigValue<VerticalConfigAlignment> verticalAlignment;
	private final AlignmentConfigValue alignment;
	private final ConfigValue<NavigationVisibility> buttonNavigationVisibility;
	private final ConfigValue<Boolean> drawBackground;

	public IngredientGridConfig(String categoryName, IConfigSchemaBuilder builder, HorizontalConfigAlignment defaultHorizontalAlignment) {
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
		alignment = new AlignmentConfigValue(categoryName, horizontalAlignment, verticalAlignment);
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
	public ConfigValue<HorizontalConfigAlignment> horizontalAlignment() {
		return horizontalAlignment;
	}

	@Override
	public ConfigValue<VerticalConfigAlignment> verticalAlignment() {
		return verticalAlignment;
	}

	public AlignmentConfigValue alignment() {
		return alignment;
	}

	@Override
	public ConfigValue<NavigationVisibility> buttonNavigationVisibility() {
		return buttonNavigationVisibility;
	}
}
