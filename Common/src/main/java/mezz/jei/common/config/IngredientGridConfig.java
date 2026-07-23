package mezz.jei.common.config;

import net.mezzdev.config.value.ConfigValueUpdateType;
import net.mezzdev.config.alignment.Alignment;
import net.mezzdev.config.alignment.HorizontalAlignment;
import net.mezzdev.config.alignment.VerticalAlignment;
import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.schema.IConfigCategoryBuilder;
import net.mezzdev.config.schema.IConfigSchemaBuilder;

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

	private final IConfigValue<Integer> maxRows;
	private final IConfigValue<Integer> maxColumns;
	private final IConfigValue<HorizontalAlignment> horizontalAlignment;
	private final IConfigValue<VerticalAlignment> verticalAlignment;
	private final IConfigValue<Alignment> alignment;
	private final IConfigValue<NavigationVisibility> buttonNavigationVisibility;
	private final IConfigValue<Boolean> drawBackground;

	public IngredientGridConfig(
		String localizationPath,
		String categoryName,
		IConfigSchemaBuilder builder,
		HorizontalAlignment defaultHorizontalAlignment
	) {
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
		alignment = new AlignmentConfigValue(localizationPath, categoryName, horizontalAlignment, verticalAlignment);
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
	public IConfigValue<Integer> maxColumns() {
		return maxColumns;
	}

	@Override
	public IConfigValue<Integer> maxRows() {
		return maxRows;
	}

	@Override
	public IConfigValue<Boolean> drawBackground() {
		return drawBackground;
	}

	@Override
	public IConfigValue<HorizontalAlignment> horizontalAlignment() {
		return horizontalAlignment;
	}

	@Override
	public IConfigValue<VerticalAlignment> verticalAlignment() {
		return verticalAlignment;
	}

	public IConfigValue<Alignment> alignment() {
		return alignment;
	}

	@Override
	public IConfigValue<NavigationVisibility> buttonNavigationVisibility() {
		return buttonNavigationVisibility;
	}
}
