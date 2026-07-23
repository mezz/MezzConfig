package net.mezzdev.config.file;

import net.mezzdev.config.value.ConfigValueSources;
import net.mezzdev.config.schema.IConfigDisplayCategory;
import net.mezzdev.config.value.IConfigValue;
import net.mezzdev.config.value.IConfigValueSource;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * User-facing config category used by config screens.
 */
public record ConfigDisplayCategory(
	String localizationPath,
	String name,
	List<IConfigValueSource> configValueSources
) implements IConfigDisplayCategory {
	public static ConfigDisplayCategory createWithValues(String localizationPath, String name, List<IConfigValue<?>> configValues) {
		return new ConfigDisplayCategory(localizationPath, name, List.of(ConfigValueSources.values(configValues)));
	}

	public ConfigDisplayCategory {
		configValueSources = List.copyOf(configValueSources);
	}

	@Override
	public Component getLocalizedName() {
		return Component.translatable(localizationPath);
	}

	@Override
	public Component getLocalizedDescription() {
		return Component.translatable(localizationPath + ".description");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<IConfigValueSource> getConfigValueSources() {
		return configValueSources;
	}
}
