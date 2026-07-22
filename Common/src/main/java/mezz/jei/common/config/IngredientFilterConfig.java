package mezz.jei.common.config;

import net.mezzdev.config.ConfigValueUpdateType;
import net.mezzdev.config.file.ConfigValue;
import net.mezzdev.config.file.IConfigCategoryBuilder;
import net.mezzdev.config.file.IConfigSchemaBuilder;

public class IngredientFilterConfig implements IIngredientFilterConfig {
	private final ConfigValue<SearchMode> modNameSearchMode;
	private final ConfigValue<SearchMode> tooltipSearchMode;
	private final ConfigValue<SearchMode> tagSearchMode;
	private final ConfigValue<SearchMode> colorSearchMode;
	private final ConfigValue<SearchMode> resourceLocationSearchMode;
	private final ConfigValue<SearchMode> creativeTabSearchMode;
	private final ConfigValue<Boolean> searchAdvancedTooltips;
	private final ConfigValue<Boolean> searchModIds;
	private final ConfigValue<Boolean> searchModAliases;
	private final ConfigValue<Boolean> searchShortModNames;
	private final ConfigValue<Boolean> searchIngredientAliases;

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
	public ConfigValue<SearchMode> modNameSearchMode() {
		return modNameSearchMode;
	}

	@Override
	public ConfigValue<SearchMode> tooltipSearchMode() {
		return tooltipSearchMode;
	}

	@Override
	public ConfigValue<SearchMode> tagSearchMode() {
		return tagSearchMode;
	}

	@Override
	public ConfigValue<SearchMode> colorSearchMode() {
		return colorSearchMode;
	}

	@Override
	public ConfigValue<SearchMode> resourceLocationSearchMode() {
		return resourceLocationSearchMode;
	}

	@Override
	public ConfigValue<SearchMode> creativeTabSearchMode() {
		return creativeTabSearchMode;
	}

	@Override
	public ConfigValue<Boolean> searchAdvancedTooltips() {
		return searchAdvancedTooltips;
	}

	@Override
	public ConfigValue<Boolean> searchModIds() {
		return searchModIds;
	}

	@Override
	public ConfigValue<Boolean> searchModAliases() {
		return searchModAliases;
	}

	@Override
	public ConfigValue<Boolean> searchIngredientAliases() {
		return searchIngredientAliases;
	}

	@Override
	public ConfigValue<Boolean> searchShortModNames() {
		return searchShortModNames;
	}
}
