package net.mezzdev.config.file;

import net.mezzdev.config.ConfigDisplayCategoryRole;
import net.mezzdev.config.IConfigDisplayCategory;
import net.mezzdev.config.IJeiConfigValue;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User-facing config category used by config screens.
 */
public record ConfigDisplayCategory(
	String localizationPath,
	String name,
	ConfigDisplayCategoryRole role,
	List<IJeiConfigValue<?>> configValues
) implements IConfigDisplayCategory {
	public ConfigDisplayCategory {
		configValues = List.copyOf(configValues);
	}

	public ConfigDisplayCategory(String localizationPath, String name, List<IJeiConfigValue<?>> configValues) {
		this(localizationPath, name, ConfigDisplayCategoryRole.DEFAULT, configValues);
	}

	public ConfigDisplayCategory withAdditionalValues(Collection<? extends IJeiConfigValue<?>> additionalValues) {
		List<IJeiConfigValue<?>> values = new ArrayList<>(configValues);
		values.addAll(additionalValues);
		return new ConfigDisplayCategory(localizationPath, name, role, values);
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
	public Component getDescription() {
		return Component.translatable(localizationPath + ".description");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Collection<IJeiConfigValue<?>> getConfigValues() {
		return configValues;
	}
}
