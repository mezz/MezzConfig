package net.mezzdev.config.schema;

import net.mezzdev.config.api.schema.IConfigDisplayCategory;
import net.mezzdev.config.api.value.IConfigValue;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

/**
 * User-facing config category used by config screens.
 */
public record ConfigDisplayCategory(
	String localizationPath,
	String name,
	List<IConfigValue<?>> configValues
) implements IConfigDisplayCategory {
	public static ConfigDisplayCategory createWithValues(String localizationPath, String name, List<IConfigValue<?>> configValues) {
		return new ConfigDisplayCategory(localizationPath, name, configValues);
	}

	public ConfigDisplayCategory {
		configValues = List.copyOf(configValues);
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
	public Collection<? extends IConfigValue<?>> getConfigValues() {
		return configValues;
	}
}
