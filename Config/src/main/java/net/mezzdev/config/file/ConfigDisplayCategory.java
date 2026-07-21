package net.mezzdev.config.file;

import net.mezzdev.config.ConfigDisplayCategoryRole;
import net.mezzdev.config.IConfigDisplayCategory;
import net.mezzdev.config.IConfigValue;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * User-facing config category used by config screens.
 */
public record ConfigDisplayCategory(
	String localizationPath,
	String name,
	ConfigDisplayCategoryRole role,
	List<IConfigValue<?>> configValues
) implements IConfigDisplayCategory {
	public ConfigDisplayCategory {
		configValues = List.copyOf(configValues);
	}

	public ConfigDisplayCategory(String localizationPath, String name, List<IConfigValue<?>> configValues) {
		this(localizationPath, name, ConfigDisplayCategoryRole.DEFAULT, configValues);
	}

	@Override
	public ConfigDisplayCategoryRole getRole() {
		return role;
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
	public List<IConfigValue<?>> getConfigValues() {
		return configValues;
	}
}
