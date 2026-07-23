package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.util.ErrorUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConfigDisplayCategoryBuilder implements IConfigDisplayCategoryBuilder {
	private final String localizationPath;
	private final String name;
	private final List<IConfigValue<?>> values = new ArrayList<>();

	public ConfigDisplayCategoryBuilder(String localizationPath, String name) {
		this.localizationPath = ErrorUtil.checkNotNull(localizationPath, "localizationPath");
		this.name = ErrorUtil.checkNotNull(name, "name");
	}

	@Override
	public ConfigDisplayCategoryBuilder addValue(IConfigValue<?> value) {
		return addValues(List.of(value));
	}

	@Override
	public ConfigDisplayCategoryBuilder addValues(Collection<? extends IConfigValue<?>> values) {
		values = ErrorUtil.checkNotNull(values, "values");
		this.values.addAll(values);
		return this;
	}

	public ConfigDisplayCategory build() {
		return new ConfigDisplayCategory(localizationPath, name, values);
	}
}
