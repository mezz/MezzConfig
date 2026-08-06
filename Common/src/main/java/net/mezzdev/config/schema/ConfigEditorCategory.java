package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigEditorCategory;
import net.mezzdev.config.util.ConfigNameUtil;
import net.mezzdev.config.util.ErrorUtil;

public class ConfigEditorCategory implements IConfigEditorCategory {
	private final String name;
	private final String localizationKey;

	public ConfigEditorCategory(String localizationKey, String name) {
		this.name = ConfigNameUtil.validateConfigName(name, "categoryName");
		this.localizationKey = ErrorUtil.checkNotNull(localizationKey, "localizationKey");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getLocalizationKey() {
		return localizationKey;
	}
}
