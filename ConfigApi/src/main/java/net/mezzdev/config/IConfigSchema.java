package net.mezzdev.config;

import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface IConfigSchema extends IJeiConfigFile {
	ConfigValueUpdateType getUpdateType(List<ConfigValueChange<?>> changes);

	@Unmodifiable
	List<? extends IConfigDisplayCategory> getDisplayCategories();
}
