package mezz.jei.common.config;

import net.mezzdev.config.value.ConfigValueUpdateType;
import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.schema.IConfigCategoryBuilder;
import net.mezzdev.config.schema.IConfigSchemaBuilder;

public class IngredientFilterConfig implements IIngredientFilterConfig {
	private final IConfigValue<SearchMode> modNameSearchMode;
	private final IConfigValue<SearchMode> tooltipSearchMode;
	private final IConfigValue<SearchMode> tagSearchMode;
	private final IConfigValue<SearchMode> colorSearchMode;
	private final IConfigValue<SearchMode> resourceLocationSearchMode;
	private final IConfigValue<SearchMode> creativeTabSearchMode;
	private final IConfigValue<Boolean> searchAdvancedTooltips;
	private final IConfigValue<Boolean> searchModIds;
	private final IConfigValue<Boolean> searchModAliases;
	private final IConfigValue<Boolean> searchShortModNames;
	private final IConfigValue<Boolean> searchIngredientAliases;

	public IngredientFilterConfig(IConfigSchemaBuilder builder) {
		IConfigCategoryBuilder search = builder.addCategory("search");
		modNameSearchMode = search.addEnum("modNameSearchMode", SearchMode.REQUIRE_PREFIX, ConfigValueUpdateType.ON_APPLY);
		tagSearchMode = search.addEnum("tagSearchMode", SearchMode.REQUIRE_PREFIX, ConfigValueUpdateType.ON_APPLY);
		tooltipSearchMode = search.addEnum("tooltipSearchMode", SearchMode.ENABLED, ConfigValueUpdateType.ON_APPLY);
		colorSearchMode = search.addEnum("colorSearchMode", SearchMode.DISABLED, ConfigValueUpdateType.ON_APPLY);
		resourceLocationSearchMode = search.addEnum("resourceLocationSearchMode", SearchMode.DISABLED, ConfigValueUpdateType.ON_APPLY);
		creativeTabSearchMode = search.addEnum("creativeTabSearchMode", SearchMode.DISABLED, ConfigValueUpdateType.ON_APPLY);
		searchAdvancedTooltips = search.addBoolean("searchAdvancedTooltips", false, ConfigValueUpdateType.ON_APPLY);
		searchModIds = search.addBoolean("searchModIds", true, ConfigValueUpdateType.ON_APPLY);
		searchModAliases = search.addBoolean("searchModAliases", true, ConfigValueUpdateType.RESTART);
		searchShortModNames = search.addBoolean("searchShortModNames", false, ConfigValueUpdateType.ON_APPLY);
		searchIngredientAliases = search.addBoolean("searchIngredientAliases", true, ConfigValueUpdateType.RESTART);
	}

	@Override
	public IConfigValue<SearchMode> modNameSearchMode() {
		return modNameSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> tooltipSearchMode() {
		return tooltipSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> tagSearchMode() {
		return tagSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> colorSearchMode() {
		return colorSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> resourceLocationSearchMode() {
		return resourceLocationSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> creativeTabSearchMode() {
		return creativeTabSearchMode;
	}

	@Override
	public IConfigValue<Boolean> searchAdvancedTooltips() {
		return searchAdvancedTooltips;
	}

	@Override
	public IConfigValue<Boolean> searchModIds() {
		return searchModIds;
	}

	@Override
	public IConfigValue<Boolean> searchModAliases() {
		return searchModAliases;
	}

	@Override
	public IConfigValue<Boolean> searchIngredientAliases() {
		return searchIngredientAliases;
	}

	@Override
	public IConfigValue<Boolean> searchShortModNames() {
		return searchShortModNames;
	}
}
