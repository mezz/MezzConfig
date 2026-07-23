package net.mezzdev.config.file;

import net.mezzdev.config.schema.IConfigDisplayCategoryBuilder;
import net.mezzdev.config.value.ConfigValueSources;
import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.value.IConfigValueSource;
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
		if (localizationPath == null) {
			throw new NullPointerException("localizationPath must not be null.");
		}
		if (name == null) {
			throw new NullPointerException("name must not be null.");
		}
		this.localizationPath = localizationPath;
		this.name = name;
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
		if (valueSource == null) {
			throw new NullPointerException("valueSource must not be null.");
		}
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
