package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.api.util.ErrorUtil;
import net.mezzdev.config.api.value.ConfigValueSources;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.IConfigValueSource;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ConfigDisplayCategoryBuilder implements IConfigDisplayCategoryBuilder {
	private final String localizationPath;
	private final String name;
	private final List<IConfigValueSource> valueSources = new ArrayList<>();

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
		return addValueSource(ConfigValueSources.values(values));
	}

	@Override
	public ConfigDisplayCategoryBuilder addValueSource(IConfigValueSource valueSource) {
		valueSource = ErrorUtil.checkNotNull(valueSource, "valueSource");
		valueSources.add(valueSource);
		return this;
	}

	@Override
	public ConfigDisplayCategoryBuilder addKeyMapping(KeyMapping keyMapping) {
		return addKeyMappings(List.of(keyMapping));
	}

	@Override
	public ConfigDisplayCategoryBuilder addKeyMappings(Collection<? extends KeyMapping> keyMappings) {
		return addValueSource(ConfigValueSources.keyMappings(keyMappings));
	}

	@Override
	public ConfigDisplayCategoryBuilder addKeyMappings(Supplier<? extends Collection<? extends KeyMapping>> keyMappingsSupplier) {
		return addValueSource(ConfigValueSources.keyMappings(keyMappingsSupplier));
	}

	public ConfigDisplayCategory build() {
		return new ConfigDisplayCategory(localizationPath, name, valueSources);
	}
}
